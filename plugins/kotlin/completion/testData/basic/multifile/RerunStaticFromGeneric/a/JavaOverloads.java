package a;

public class JavaOverloads {
    public static String somePrefixJavaStaticField = "staticField";
    public String somePrefixJavaInstanceField = "instanceField";

    public static <T> T somePrefixJavaStaticMethod(T t) { return t; }
    public <T> T somePrefixJavaInstanceMethod(T t) { return t; }
}

// ALLOW_AST_ACCESS
