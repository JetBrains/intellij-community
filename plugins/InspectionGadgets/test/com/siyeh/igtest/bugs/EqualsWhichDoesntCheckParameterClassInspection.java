package com.siyeh.igtest.bugs;

public class EqualsWhichDoesntCheckParameterClassInspection {
    private int foo;

    public boolean equals(Object o) {
        if (this == o) return true;
        //if (o instanceof EqualsWhichDoesntCheckParameterClassInspection) return false;
        //if (getClass() != o.getClass()) return false;

        final EqualsWhichDoesntCheckParameterClassInspection equalsWhichDoesntCheckParameterClassInspection = (EqualsWhichDoesntCheckParameterClassInspection) o;

        if (foo != equalsWhichDoesntCheckParameterClassInspection.foo) return false;

        return true;
    }

    public int hashCode() {
        return foo;
    }
}
