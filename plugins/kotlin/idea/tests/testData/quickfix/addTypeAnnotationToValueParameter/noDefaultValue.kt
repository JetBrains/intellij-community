// "class org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix" "false"
// ERROR: A type annotation is required on a value parameter
// ACTION: Create test
// ACTION: Convert to secondary constructor
// K2_AFTER_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE

class Foo(val bar<caret>)