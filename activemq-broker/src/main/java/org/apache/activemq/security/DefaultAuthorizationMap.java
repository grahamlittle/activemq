/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.security;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.filter.DestinationMap;
import org.apache.activemq.filter.DestinationMapEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Represents a destination based configuration of policies so that individual
 * destinations or wildcard hierarchies of destinations can be configured using
 * different policies. Each entry in the map represents the authorization ACLs
 * for each operation.
 *
 *
 */
public class DefaultAuthorizationMap extends DestinationMap implements AuthorizationMap {

    public static final String DEFAULT_GROUP_CLASS = "org.apache.activemq.jaas.GroupPrincipal";

    private AuthorizationEntry defaultEntry;

    private TempDestinationAuthorizationEntry tempDestinationAuthorizationEntry;

    protected String groupClass = DEFAULT_GROUP_CLASS;

    public DefaultAuthorizationMap() {
    }

    @SuppressWarnings("rawtypes")
    public DefaultAuthorizationMap(List<DestinationMapEntry> authorizationEntries) {
        setAuthorizationEntries(authorizationEntries);

    }

    public void setTempDestinationAuthorizationEntry(TempDestinationAuthorizationEntry tempDestinationAuthorizationEntry) {
        this.tempDestinationAuthorizationEntry = tempDestinationAuthorizationEntry;
    }

    public TempDestinationAuthorizationEntry getTempDestinationAuthorizationEntry() {
        return this.tempDestinationAuthorizationEntry;
    }

    public Set<Object> getTempDestinationAdminACLs() {
        if (tempDestinationAuthorizationEntry != null) {
            return tempDestinationAuthorizationEntry.getAdminACLs();
        } else {
            return null;
        }
    }

    public Set<Object> getTempDestinationReadACLs() {
        if (tempDestinationAuthorizationEntry != null) {
            return tempDestinationAuthorizationEntry.getReadACLs();
        } else {
            return null;
        }
    }

    public Set<Object> getTempDestinationWriteACLs() {
        if (tempDestinationAuthorizationEntry != null) {
            return tempDestinationAuthorizationEntry.getWriteACLs();
        } else {
            return null;
        }
    }

    public Set<Object> getAdminACLs(ActiveMQDestination destination) {
        Set<AuthorizationEntry> entries = getAllEntries(destination);
        Set<Object> answer = new HashSet<Object>();
        // now lets go through each entry adding individual
        for (Iterator<AuthorizationEntry> iter = entries.iterator(); iter.hasNext();) {
            AuthorizationEntry entry = iter.next();
            answer.addAll(entry.getAdminACLs());
        }
        return answer;
    }

    public Set<Object> getReadACLs(ActiveMQDestination destination) {
        Set<AuthorizationEntry> entries = getAllEntries(destination);
        Set<Object> answer = new HashSet<Object>();

        // now lets go through each entry adding individual
        for (Iterator<AuthorizationEntry> iter = entries.iterator(); iter.hasNext();) {
            AuthorizationEntry entry = iter.next();
            answer.addAll(entry.getReadACLs());
        }
        return answer;
    }

    public Set<Object> getWriteACLs(ActiveMQDestination destination) {
        Set<AuthorizationEntry> entries = getAllEntries(destination);
        Set<Object> answer = new HashSet<Object>();

        // now lets go through each entry adding individual
        for (Iterator<AuthorizationEntry> iter = entries.iterator(); iter.hasNext();) {
            AuthorizationEntry entry = iter.next();
            answer.addAll(entry.getWriteACLs());
        }
        return answer;
    }

    public AuthorizationEntry getEntryFor(ActiveMQDestination destination) {
        AuthorizationEntry answer = (AuthorizationEntry)chooseValue(destination);
        if (answer == null) {
            answer = getDefaultEntry();
        }
        return answer;
    }


    /**
     * Looks up the value(s) matching the given Destination key. For simple
     * destinations this is typically a List of one single value, for wildcards
     * or composite destinations this will typically be a Union of matching
     * values.
     *
     * @param key the destination to lookup
     * @return a Union of matching values or an empty list if there are no
     *         matching values.
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized Set get(ActiveMQDestination key) {
        if (key.isComposite()) {
            ActiveMQDestination[] destinations = key.getCompositeDestinations();
            Set answer = null;
            for (int i = 0; i < destinations.length; i++) {
                ActiveMQDestination childDestination = destinations[i];
                answer = union(answer, get(childDestination));
                if (answer == null  || answer.isEmpty()) {
                    break;
                }
            }
            return answer;
        }
        return findWildcardMatches(key);
    }


    /**
     * Sets the individual entries on the authorization map
     */
    @SuppressWarnings("rawtypes")
    public void setAuthorizationEntries(List<DestinationMapEntry> entries) {
        super.setEntries(entries);
    }

    public AuthorizationEntry getDefaultEntry() {
        return defaultEntry;
    }

    public void setDefaultEntry(AuthorizationEntry defaultEntry) {
        this.defaultEntry = defaultEntry;
    }

    @SuppressWarnings("rawtypes")
    protected Class<? extends DestinationMapEntry> getEntryClass() {
        return AuthorizationEntry.class;
    }

    @SuppressWarnings("unchecked")
    protected Set<AuthorizationEntry> getAllEntries(ActiveMQDestination destination) {
        Set<AuthorizationEntry> entries = get(destination);
        if (defaultEntry != null) {
            entries.add(defaultEntry);
        }
        return entries;
    }

    public String getGroupClass() {
        return groupClass;
    }

    public void setGroupClass(String groupClass) {
        this.groupClass = groupClass;
    }

    public static Object createGroupPrincipal(String name, String groupClass) throws Exception {
        Object[] param = new Object[]{name};

        Class<?> cls = Class.forName(groupClass);

        Constructor<?>[] constructors = cls.getConstructors();
        int i;
        Object instance;
        for (i = 0; i < constructors.length; i++) {
            Class<?>[] paramTypes = constructors[i].getParameterTypes();
            if (paramTypes.length != 0 && paramTypes[0].equals(String.class)) {
                break;
            }
        }
        if (i < constructors.length) {
            instance = constructors[i].newInstance(param);
        } else {
            instance = cls.newInstance();
            Method[] methods = cls.getMethods();
            i = 0;
            for (i = 0; i < methods.length; i++) {
                Class<?>[] paramTypes = methods[i].getParameterTypes();
                if (paramTypes.length != 0 && methods[i].getName().equals("setName") && paramTypes[0].equals(String.class)) {
                    break;
                }
            }

            if (i < methods.length) {
                methods[i].invoke(instance, param);
            } else {
                throw new NoSuchMethodException();
            }
        }

        return instance;
    }

}
