package com.siyeh.ig;

import com.intellij.codeInspection.LocalInspectionTool;

import java.util.Comparator;

class InspectionComparator implements Comparator {
    InspectionComparator() {
        super();
    }

    public int compare(Object o1, Object o2) {
        final Class class1 = (Class) o1;
        final Class class2 = (Class) o2;
        final LocalInspectionTool inspection1;
        final LocalInspectionTool inspection2;
        try {
            inspection1 = (LocalInspectionTool) class1.newInstance();
            inspection2 = (LocalInspectionTool) class2.newInstance();
        } catch (InstantiationException e) {
            return -1;
        } catch (IllegalAccessException e) {
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
        displayName1 = stripLeadingNonCharacters(displayName1);
        displayName2 = stripLeadingNonCharacters(displayName2);
        return displayName1.compareTo(displayName2);
    }

    private static String stripLeadingNonCharacters(String str) {
        for (int i = 0; i < str.length(); i++) {
            final char ch = str.charAt(i);
            if (Character.isLetter(ch)) {
                return str.substring(i);
            }
        }
        return str;
    }
}
