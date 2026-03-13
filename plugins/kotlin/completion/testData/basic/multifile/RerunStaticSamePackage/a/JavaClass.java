package a;

public class JavaClass {
  static String somePrefixJavaStaticFieldA = "staticField";
  private static String somePrefixJavaStaticFieldB = "staticField";
  protected static String somePrefixJavaStaticFieldC = "staticField";
  static String somePrefixJavaStaticMethodA() { return "staticMethod"; }
  private static String somePrefixJavaStaticMethodB() { return "staticMethod"; }
  protected static String somePrefixJavaStaticMethodC() { return "staticMethod"; }
}

// ALLOW_AST_ACCESS