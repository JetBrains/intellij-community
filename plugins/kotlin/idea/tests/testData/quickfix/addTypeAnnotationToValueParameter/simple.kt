// "Add type 'Int' to parameter 'bar'" "true"

class Foo(val bar = 10<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddTypeAnnotationToValueParameterFix