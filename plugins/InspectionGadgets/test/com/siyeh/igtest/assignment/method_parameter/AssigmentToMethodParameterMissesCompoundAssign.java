package com.siyeh.igtest.assignment.method_parameter;

class AssigmentToMethodParameterMissesCompoundAssign {

    public void incrementParameter(int value) {
        value++; // not flagged by the inspection
    }

    public void compoundAssignParameter(int value) {
        value += 1;  // flagged by the inspection
    }

    public void compoundAssignParameter(int value, int increment) {
        value += increment; // flagged by the inspection
    }

    public void foo(String s) {
        System.out.println(s);
        s = "other";
        System.out.println(s);
    }

    public void method(int decreased, int increased) {
        decreased += 10; // not highlighted
        increased -= 10; // highlighted
    }

}