// "Add type 'Int' to parameter 'bar'" "true"
// K2_ERROR: VALUE_PARAMETER_WITHOUT_EXPLICIT_TYPE

class Foo(val bar = 10<caret>)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix