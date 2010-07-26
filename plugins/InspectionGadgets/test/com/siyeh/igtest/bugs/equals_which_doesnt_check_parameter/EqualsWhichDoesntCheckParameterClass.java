package com.siyeh.igtest.bugs.equals_which_doesnt_check_parameter;

public class EqualsWhichDoesntCheckParameterClass {
    private int foo;

    public boolean equals(Object o) {
        if (this == o) return true;
        //if (o instanceof EqualsWhichDoesntCheckParameterClassInspection) return false;
        //if (getClass() != o.getClass()) return false;

        final EqualsWhichDoesntCheckParameterClass equalsWhichDoesntCheckParameterClass = (EqualsWhichDoesntCheckParameterClass) o;

        if (foo != equalsWhichDoesntCheckParameterClass.foo) return false;

        return true;
    }

    public int hashCode() {
        return foo;
    }
}
class One {
    private int one;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof One)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return one;
    }
}
class Two {
    private int two;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return two;
    }
}
class Three {
    private int three;

    @Override
    public boolean equals(Object o) {
        if (!getClass().isInstance(o)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return three;
    }
}

