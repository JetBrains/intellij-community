// "Create type parameter in class 'X'" "true"

class X

fun foo(x: X<<caret>String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createTypeParameter.CreateTypeParameterFromUsageFix