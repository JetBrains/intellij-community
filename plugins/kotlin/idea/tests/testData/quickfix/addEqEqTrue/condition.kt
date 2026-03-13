// "Add '== true'" "true"
// K2_ERROR: Condition type mismatch: inferred type is 'Boolean?' but 'Boolean' was expected.
class Foo {
    fun bar() = true
}

fun test(foo: Foo?) {
    if (foo?.bar()<caret>) {
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix