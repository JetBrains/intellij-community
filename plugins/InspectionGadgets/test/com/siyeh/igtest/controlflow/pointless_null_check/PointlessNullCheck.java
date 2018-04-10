package com.siyeh.igtest.controlflow.pointless_null_check;

public class PointlessNullCheck {

    public void testViolations(Object arg) {
        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">arg != null</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">null != arg</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">arg == null</warning> || !(arg instanceof String)) {
            System.out.println("this should trigger a warning");
        }

        if ((<warning descr="Unnecessary 'null' check before 'instanceof' expression">(arg) != (null)</warning>) && ((arg) instanceof String)) {
          System.out.println("this should trigger a warning");
        }
        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">arg != null</warning> && (arg instanceof String || arg instanceof Integer)) {
            System.out.println("this should trigger a warning");
        }
     }

    String arg1 = "foo";

    public void testMethods(Object obj, Object obj1, Object obj2) {
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && check(obj)) {
            System.out.println("ok");
        }
        if(<warning descr="Unnecessary 'null' check before 'check1()' call">obj1 != null</warning> && obj2 != null && check1(obj1, obj2)) {
            System.out.println("ok");
        }
        if(obj1 != null && <warning descr="Unnecessary 'null' check before 'check2()' call">obj2 != null</warning> && check2(obj1, obj2)) {
            System.out.println("ok");
        }
        if(obj != null && check1(obj, obj.toString())) {
            System.out.println("ok");
        }
    }

    private boolean check(Object obj) {
        if(obj == null) return false;
        return obj.hashCode() > 10;
    }

    private boolean check1(Object obj1, Object obj2) {
        if(obj1 == null) return false;
        return obj1.hashCode() > 10;
    }

    private boolean check2(Object obj1, Object obj2) {
        if(obj2 == null) return false;
        return obj2.hashCode() > 10;
    }

    public void testPassingCases(String arg1, String arg2) {
        if (arg1 != null || arg1 instanceof String) {
            System.out.println("this should not trigger a warning");
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

    void testQualified(Object obj) {
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && check(obj)) System.out.println(1);
        if(<warning descr="Unnecessary 'null' check before 'check()' call">obj != null</warning> && this.check(obj)) System.out.println(1);
        // ctor called only if obj is non-null: removing it may change the semantics
        if(obj != null && new PointlessNullCheck().check(obj)) System.out.println(1);
        if(<warning descr="Unnecessary 'null' check before 'check2()' call">obj != null</warning> && check2(null, obj)) System.out.println(1);
        // argument side effect
        if(obj != null && check2(new PointlessNullCheck(), obj)) System.out.println(1);
    }
}
