/*
 * @author max
 */
package org.jetbrains.generate.tostring.config;

public enum InsertWhere {
    AT_CARET("At caret"),
    AFTER_EQUALS_AND_HASHCODE("After equals() and hashCode()"),
    AT_THE_END_OF_A_CLASS("At the end of class");

    private String displayName;

    InsertWhere(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}