package com.siyeh.igtest.controlflow.pointless_null_check;

public class PointlessNullCheck {

    public void testViolations(Object arg) {
        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">arg != null</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (<warning descr="Unnecessary 'null' check before 'instanceof' expression">null != arg</warning> && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (arg instanceof String && <warning descr="Unnecessary 'null' check after 'instanceof' expression">null != arg</warning>) {
            System.out.println("this should trigger a warning");
        }

        if (arg instanceof String && <warning descr="Unnecessary 'null' check after 'instanceof' expression">arg != null</warning>) {
            System.out.println("this should trigger a warning");
        }

        if ((arg instanceof String) && (<warning descr="Unnecessary 'null' check after 'instanceof' expression">arg != null</warning>)) {
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

        if (arg instanceof String && arg.equals(arg) && <warning descr="Unnecessary 'null' check after 'instanceof' expression">arg != null</warning>) {
            System.out.println("warning");
        }
     }

    String arg1 = "foo";

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
}
