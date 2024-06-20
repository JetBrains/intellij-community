// "Add '== true'" "true"
class Foo {
    fun bar() = true
}

fun test(foo: Foo?) {
    if (foo?.bar()<caret>) {
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix