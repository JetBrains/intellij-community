package a;

public class JavaClass {
  public static String somePrefixJavaStaticField = "staticField";
  public String somePrefixJavaInstanceField = "instanceField";

  public static String somePrefixJavaStaticMethod() { return "staticMethod"; }
  public String somePrefixJavaInstanceMethod() { return "instanceMethod"; }
}

// ALLOW_AST_ACCESS