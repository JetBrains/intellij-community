package com.siyeh.igtest.bugs.throwable_result_of_method_call_ignored;



public class A {
    public static void test() {
        try {
            firstNonNull(new Throwable(), null);
        }
        catch (Exception e) {
            throw new RuntimeException(firstNonNull(e.getCause(), e));
        }
    }

    public static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
