package com.siyeh.igtest.controlflow.pointless_null_check;

public class PointlessNullCheck {

    public void testViolations(Object arg) {
        if (arg != null && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (null != arg && arg instanceof String) {
            System.out.println("this should trigger a warning");
        }

        if (arg instanceof String && null != arg) {
            System.out.println("this should trigger a warning");
        }

        if (arg instanceof String && arg != null) {
            System.out.println("this should trigger a warning");
        }

        if ((arg instanceof String) && (arg != null)) {
            System.out.println("this should trigger a warning");
        }

        if (arg == null || !(arg instanceof String)) {
            System.out.println("this should trigger a warning");
        }

        if (((arg) != (null)) && ((arg) instanceof String)) {
          System.out.println("this should trigger a warning");
        }
        if (arg != null && (arg instanceof String || arg instanceof Integer)) {
            System.out.println("this should trigger a warning");
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

        if (arg.charAt(5) instanceof String && arg.charAt(5) != null) {
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
    }
}
