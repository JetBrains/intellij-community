// "Remove parameter 'x'" "true"
class Foo<X> {
    constructor(<caret>x: X)
}

val foo = Foo(1)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix