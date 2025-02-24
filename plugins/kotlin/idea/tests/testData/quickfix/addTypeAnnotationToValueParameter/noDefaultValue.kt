// "class org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix" "false"
// ERROR: A type annotation is required on a value parameter
// ACTION: Create test
// ACTION: Convert to secondary constructor
// K2_AFTER_ERROR: An explicit type is required on a value parameter.

class Foo(val bar<caret>)