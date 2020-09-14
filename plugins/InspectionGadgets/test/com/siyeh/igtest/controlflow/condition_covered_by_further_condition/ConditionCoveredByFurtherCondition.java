package com.siyeh.igtest.controlflow.pointless_null_check;

import org.jetbrains.annotations.NotNull;

public class ConditionCoveredByFurtherCondition {
    static class C {
        public static final Object C1 = new C();
        public static final Object C2 = new C();
        public void m() {
            if (this == C1 || this == C2) { /* ...*/ }
        }
        
        public static final Object C3 = getC();
        public static final Object C4 = getC();
        
        public void m2() {
            if (this == C3 || this == C4) {}
        }
        
        private static Object getC() {
            return new C();
        }
    }
    
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

    void testAlwaysTrue(@NotNull Object obj, Object obj2) {
        // obj != null is true, but does not depend on the second condition, do not report it (will be reported by CC&E)
        if(obj != null && obj2 != null) {

        }
    }

    void testDereferenceNotNull(@NotNull Object obj) {
        if(obj != null && obj.hashCode() == 10) {}
    }

    void testDereference(Object obj) {
        if(obj != null && obj.hashCode() == 10) {}
    }

    void testIncomplete(String s) {
        if(s != null && <error descr="Operator '!' cannot be applied to 'java.lang.String'">!s</error>) {}
        if(<error descr="Operator '&&' cannot be applied to 'boolean', 'java.lang.String'">s != null && s</error>) {}
    }

    void testUnboxing(Integer x, Boolean b) {
        if(x != null && x > 5) {}
        if(b != null && b) {}
    }

    void testEnum(X x) {
        if(<warning descr="Condition 'x != null' covered by subsequent condition 'x == X.A'">x != null</warning> && x == X.A) {}
        if(<warning descr="Condition 'x != X.A' covered by subsequent condition 'x == X.B'">x != X.A</warning> && x == X.B) {}
        if(<warning descr="Condition 'x == X.A' covered by subsequent condition 'x != X.C'">x == X.A</warning> || x != X.C) {}
    }

    void testDereferenceOk(int[] arr1, int[] arr2) {
        if(<warning descr="Condition 'arr1.length == 0' covered by subsequent conditions">arr1.length == 0</warning> || arr2.length == 0 || arr1.length != arr2.length) {

        }
    }

    void testErrorElement(Object obj) {
        if(!(obj instanceof Integer) && !(obj instanceof Long) && !(obj<error descr="')' expected"><error descr="')' expected"> </error></error>Number<error descr="';' expected"><error descr="Unexpected token">)</error></error><error descr="Unexpected token">)</error> {}
        if(<warning descr="Condition '!(obj instanceof Integer)' covered by subsequent condition '!(obj instanceof Number)'">!(obj instanceof Integer)</warning> && !(obj instanceof Number) && !(obj<error descr="')' expected"><error descr="')' expected"> </error></error>Number<error descr="';' expected"><error descr="Unexpected token">)</error></error><error descr="Unexpected token">)</error> {}
    }

    void testErrorElement2(char ch) {
        if(ch != ']' && ch != <error descr="Unclosed character literal">'})</error><EOLError descr="')' expected"></EOLError>
    }

    void testInstanceOfUnknown(Object obj) {
        if(obj instanceof <error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error> || obj == null) { }
        if((obj instanceof <error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error>) || obj == null) { }
    }

    void testIncompleteLambda(Object x) {
        if (x != null && <error descr="Lambda expression not expected here">() -> x</error><EOLError descr="')' expected"></EOLError>
    }

    void testIncompleteLambda2(Object x) {
        if (x != null && () -> x instanceof<error descr="')' expected"><error descr="Type expected"> </error></error>
    }
    
    void testBooleanChain(boolean b1, boolean b2) {
        if (<warning descr="Condition '(b1 || b2)' covered by subsequent condition 'b1 != b2'">(b1 || b2)</warning> && b1 != b2) {}
    }

    void testTwoInstanceOf(Object object) {
        if (<warning descr="Condition 'object != null' covered by subsequent condition 'object instanceof String || object instanceof Number'">object != null</warning> && (object instanceof String || object instanceof Number)) {}
    }

    class A {int value;}
    class AA extends A {}

    public boolean testDerefInBetween(A x) {
        return x != null && x.value > 0 && x instanceof AA;
    }

    native Object getFoo();

    public void testOr(@NotNull Object obj) {
        obj = getFoo();
        if (obj == null || obj instanceof String) {}
    }
    static void test(Object obj) {
        if (obj == Holder.x || obj instanceof CharSequence) {}
    }

    static class Holder {
        static final Object x = new Object();
    }

    void testChainInstanceof(String arg) {
        if ((<error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.lang.Integer'">arg instanceof Integer</error>) || <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.lang.Long'">arg instanceof Long</error>) {}
        if (<error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.lang.Integer'">arg instanceof Integer</error> || <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.lang.Long'">arg instanceof Long</error>) {}
    }
}
enum X {A, B, C}