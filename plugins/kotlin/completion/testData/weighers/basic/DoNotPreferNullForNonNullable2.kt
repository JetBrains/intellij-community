package test

class Test
val nullNullable: Test? = null

fun foo(a: Test) {}

fun bar() {
    foo(nul<caret>)
}

// ORDER: nullNullable
// ORDER: null