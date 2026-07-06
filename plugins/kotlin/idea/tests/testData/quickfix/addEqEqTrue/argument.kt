// "Add '== true'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
class Foo {
    fun bar() = true
}

fun baz(b: Boolean) {}

fun test(foo: Foo?) {
    baz(foo?.bar()<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix