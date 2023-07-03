// KTIJ-24026
// ALLOW_ERRORS

class <!HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ClassB<!> {}

class <!HIGHLIGHTING("severity='ERROR'; descr='[PACKAGE_OR_CLASSIFIER_REDECLARATION] Redeclaration: [class ClassA : ClassB]'"), HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ClassA<!>: <!HIGHLIGHTING("severity='ERROR'; descr='[FINAL_SUPERTYPE] This type is final, so it cannot be inherited from'"), HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>ClassB<!>() {}
class <!HIGHLIGHTING("severity='ERROR'; descr='[PACKAGE_OR_CLASSIFIER_REDECLARATION] Redeclaration: [class ClassA : ClassB]'"), HIGHLIGHTING("textAttributesKey='KOTLIN_CLASS'")!>ClassA<!>: <!HIGHLIGHTING("severity='ERROR'; descr='[FINAL_SUPERTYPE] This type is final, so it cannot be inherited from'"), HIGHLIGHTING("textAttributesKey='KOTLIN_CONSTRUCTOR'")!>ClassB<!>() {}
