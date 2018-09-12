package com.siyeh.igtest.controlflow.pointless_null_check;

import org.jetbrains.annotations.NotNull;

public class ConditionCoveredByFurtherCondition {

    public void testInstanceOf(Object arg) {
        if (<warning descr="Condition 'arg != null' covered by subsequent condition 'arg instanceof String'">arg != null</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Condition 'null != arg' covered by subsequent condition 'arg instanceof String'">null != arg</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Condition 'arg == null' covered by subsequent condition '!(arg instanceof String)'">arg == null</warning> || !(arg instanceof String)) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Condition '((arg) != (null))' covered by subsequent condition '(arg) instanceof String'">((arg) != (null))</warning> && ((arg) instanceof String)) {
          System.out.println("this should trigger a warning");
        }
        if (<warning descr="Condition 'arg != null' covered by subsequent condition 'arg instanceof String || arg instanceof Integer'">arg != null</warning> && (arg instanceof String || arg instanceof Integer)) {
            System.out.println("this should trigger a warning");
        }
     }

    String arg1 = "foo";

    public void testPassingCases(String arg1, String arg2) {
        if (<warning descr="Condition 'arg1 != null' covered by subsequent condition 'arg1 instanceof String'">arg1 != null</warning> || arg1 instanceof String) {
            System.out.println("not very nice, but harmless: DFA would warn anyways on arg1 instanceof String");
        }

        if (arg1 == null && arg1 instanceof String) {
            System.out.println("this should not trigger a warning");
        }

        if (arg1 != null && arg2 instanceof String) {
            System.out.println("this should not trigger a warning");
        }

        if (arg1.substring(5) instanceof String && arg1.substring(5) != null) {
            System.out.println("this should not trigger a warning");
        }

        if (arg1 != null && arg1.length() > 2) {
            System.out.println("this should not trigger a warning");
        }

        if (arg1 != "hello" || arg1 instanceof String) {
            System.out.println("this should not trigger a warning");
        }
        if (this.arg1 != null && arg1 instanceof String) {
            System.out.println("this should not trigger a warning");
        }

        if (arg1 != null && arg1.equals(arg1) && arg1 instanceof String) {
            System.out.println("no warning");
        }
    }

    void testInstanceofChains(Object obj) {
        if(<warning descr="Condition 'obj instanceof Integer' covered by subsequent condition 'obj instanceof Number'">obj instanceof Integer</warning> || <warning descr="Condition 'obj instanceof Long' covered by subsequent condition 'obj instanceof Number'">obj instanceof Long</warning> || obj instanceof Number) {}

        if(<warning descr="Condition 'obj instanceof Integer' covered by subsequent condition 'obj != null'">obj instanceof Integer</warning> || obj != null) {}

        if (<warning descr="Condition '!(obj instanceof Integer)' covered by subsequent condition '!(obj instanceof Number)'">!(obj instanceof Integer)</warning> && <warning descr="Condition '!(obj instanceof Long)' covered by subsequent condition '!(obj instanceof Number)'">!(obj instanceof Long)</warning> && !(obj instanceof Number)) {}
    }

    void testIntegralRange(int x, long y) {
        if(<warning descr="Condition 'x != -1' covered by subsequent condition 'x > 10'">x != -1</warning> && x > 10 && x < 1000) {}
        if(<warning descr="Condition 'x < 100' covered by subsequent condition 'x < 0'">x < 100</warning> && x < 0) {}
        if(<warning descr="Condition 'y == 0' covered by subsequent condition 'y < 100'">y == 0</warning> || x == 1 || y < 100 || x == 5) {}
    }

    void testTransitive(int x, int y, int z) {
        if(<warning descr="Condition 'x > 0' covered by subsequent conditions">x > 0</warning> && y > 0 && x > y) {}
        if(<warning descr="Condition 'x > z' covered by subsequent conditions">x > z</warning> && y > z && x > y) {}
        if(<warning descr="Condition 'x > 0' covered by subsequent conditions">x > 0</warning> && y > 0 && x == y) {}
    }

    void testNotNull(Object obj, @NotNull Object obj1) {
        if(<warning descr="Condition 'obj != null' covered by subsequent condition 'obj == obj1'">obj != null</warning> && obj == obj1) {}
    }

    void testRedundantBooleanCheck(Object obj, boolean b) {
        if(<warning descr="Condition '(b && obj instanceof String)' covered by subsequent condition 'obj instanceof String'">(b && obj instanceof String)</warning> || (obj instanceof String)) {}
    }

    void testNullCheck(Object o1, Object o2) {
        if(<warning descr="Condition '(o1 != null || o2 != null)' covered by subsequent condition 'o1 != o2'">(o1 != null || o2 != null)</warning> && o1 != o2) {}
    }
}
