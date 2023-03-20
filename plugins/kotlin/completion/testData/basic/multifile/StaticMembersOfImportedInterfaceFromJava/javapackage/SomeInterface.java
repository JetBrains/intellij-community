package javapackage;

public interface SomeInterface {
    public void aProc() {}
    public static void aStaticProc() {}

    public static String getA() {
        return CONST_A;
    }

    public final String CONST_A = "A";

    public static class FooBar {}
}