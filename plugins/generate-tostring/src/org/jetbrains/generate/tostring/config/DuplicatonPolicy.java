/*
 * @author max
 */
package org.jetbrains.generate.tostring.config;

public enum DuplicatonPolicy {
    ASK("Ask"),
    REPLACE("Replace existing"),
    DUPLICATE("Generate duplicating method");

    private final String displayName;

    DuplicatonPolicy(String displayName) {
        this.displayName = displayName;
    }


    @Override
    public String toString() {
        return displayName;
    }
}
