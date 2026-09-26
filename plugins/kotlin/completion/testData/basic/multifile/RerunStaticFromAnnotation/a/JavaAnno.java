package a;

public @interface JavaAnno {
    String somePrefixJavaStaticField = "annoConst";
    String somePrefixJavaInstanceMethod() default "instanceMethod";
}

// ALLOW_AST_ACCESS