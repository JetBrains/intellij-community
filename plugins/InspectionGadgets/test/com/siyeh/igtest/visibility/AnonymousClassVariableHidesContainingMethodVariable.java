package com.siyeh.igtest.visibility;

class AnonymousClassVariableHidesContainingMethodVariable {
    String s;
    String t;

    Object foo(String s) {
        String t, u = "";
        return new Object() {
            void foo(String t) {
                String s = " ";
            }
        };
    }
}
