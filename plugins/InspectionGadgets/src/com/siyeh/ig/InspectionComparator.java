/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig;

import com.intellij.codeInspection.InspectionProfileEntry;

import java.util.Comparator;

class InspectionComparator
        implements Comparator<Class<? extends InspectionProfileEntry>> {

    public int compare(Class<? extends InspectionProfileEntry> class1,
                       Class<? extends InspectionProfileEntry> class2) {
        final InspectionProfileEntry inspection1;
        final InspectionProfileEntry inspection2;
        try {
            inspection1 = class1.newInstance();
            inspection2 = class2.newInstance();
        } catch (InstantiationException ignore) {
            return -1;
        } catch (IllegalAccessException ignore) {
            return -1;
        }
        final String groupName1 = inspection1.getGroupDisplayName();
        final String groupName2 = inspection2.getGroupDisplayName();
        final int groupNameComparison = groupName1.compareTo(groupName2);
        if (groupNameComparison != 0) {
            return groupNameComparison;
        }
        String displayName1 = inspection1.getDisplayName();
        String displayName2 = inspection2.getDisplayName();
        displayName1 = displayName1.toUpperCase();
        displayName2 = displayName2.toUpperCase();
        displayName1 = stripQuotes(displayName1);
        displayName2 = stripQuotes(displayName2);
        return displayName1.compareTo(displayName2);
    }

    private static String stripQuotes(String str) {
        if(str.indexOf((int) '\'') <0 && str.indexOf((int) '"')<0) {
            return str;
        }
        final int length = str.length();
        final StringBuffer buffer = new StringBuffer(length);
        for (int i = 0; i < length; i++) {
            final char ch = str.charAt(i);
            if (ch != '"' && ch != '\'') {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }
}