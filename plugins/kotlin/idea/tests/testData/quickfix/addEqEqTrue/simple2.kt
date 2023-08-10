// "Add '== true'" "true"
class Foo {
    fun bar() = true
}

fun baz(b: Boolean) {}

fun test(foo: Foo?) {
    baz(foo?.bar()<caret>)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddEqEqTrueFix