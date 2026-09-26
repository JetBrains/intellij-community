package a;

public enum JavaEnum {
  A, B;

  public static String somePrefixJavaStaticField = "staticField";
  public String somePrefixJavaInstanceField = "instanceField";

  public static String somePrefixJavaStaticMethod() { return "staticMethod"; }
  public String somePrefixJavaInstanceMethod() { return "instanceMethod"; }
}

// ALLOW_AST_ACCESS
