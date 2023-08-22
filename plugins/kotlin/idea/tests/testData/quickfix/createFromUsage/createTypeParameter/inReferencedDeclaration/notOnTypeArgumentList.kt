// "Create type parameter in class 'X'" "true"
// ACTION: Create type parameter in class 'X'
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias

class X

fun foo(x: <caret>X<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix