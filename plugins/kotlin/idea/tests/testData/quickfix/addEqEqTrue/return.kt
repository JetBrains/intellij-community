// "Add '== true'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
class Foo {
    fun bar() = true
}

fun test(foo: Foo?): Boolean {
    return foo?.bar()<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix