package com.siyeh.igtest.assignment.method_parameter;

class AssignmentToMethodParameterMissesCompoundAssign {

    public void incrementParameter(int value) {
        <warning descr="Assignment to method parameter 'value'">value</warning>++; // not flagged by the inspection
    }

    public void compoundAssignParameter(int value) {
        <warning descr="Assignment to method parameter 'value'">value</warning> += 1;  // flagged by the inspection
    }

    public void compoundAssignParameter(int value, int increment) {
        <warning descr="Assignment to method parameter 'value'">value</warning> += increment; // flagged by the inspection
    }

    public void foo(String s) {
        System.out.println(s);
        <warning descr="Assignment to method parameter 's'">s</warning> = "other";
        System.out.println(s);
    }

    public void method(int decreased, int increased) {
        <warning descr="Assignment to method parameter 'decreased'">decreased</warning> += 10; // not highlighted
        <warning descr="Assignment to method parameter 'increased'">increased</warning> -= 10; // highlighted
    }

    public void parenthesized(int p) {
        System.out.println(p);
        <warning descr="Assignment to method parameter '(p)'">(p)</warning> = 1;
    }

    public void leaveMyLambdaAlone() {
        Consumer c = (p) -> {
            p = null;
            System.out.println(p);
        };
    }

    interface Consumer {
        void m(Object o);
    }

}