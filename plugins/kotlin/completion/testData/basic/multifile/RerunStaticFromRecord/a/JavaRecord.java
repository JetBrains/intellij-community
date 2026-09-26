package a;

public record JavaRecord(String somePrefixJavaInstanceFieldA) {
    public static String somePrefixJavaStaticField = "staticField";
    public String somePrefixJavaInstanceFieldB = "instanceField";

    public static String somePrefixJavaStaticMethod() { return "staticMethod"; }
    public String somePrefixJavaInstanceMethod() { return "instanceMethod"; }
}

// ALLOW_AST_ACCESS
