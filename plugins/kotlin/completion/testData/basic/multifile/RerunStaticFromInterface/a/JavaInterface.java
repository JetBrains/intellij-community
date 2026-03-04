package a;

public interface JavaInterface {
    public static String somePrefixJavaStaticFieldA = "staticField";
    public static String somePrefixJavaStaticMethodA() { return "staticMethod"; }
    static String somePrefixJavaStaticFieldB = "staticField";
    String somePrefixJavaStaticFieldC = "staticField";
    static String somePrefixJavaStaticMethodB() { return "staticMethod"; }

    public String somePrefixJavaInstanceMethod();
}

// ALLOW_AST_ACCESS