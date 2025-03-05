// FIR_IDENTICAL
// FIR_COMPARISON

object Foo {

    fun foo() {}
}

fun bar() {
    foo<caret>
}

// ELEMENT: foo
// INVOCATION_COUNT: 2