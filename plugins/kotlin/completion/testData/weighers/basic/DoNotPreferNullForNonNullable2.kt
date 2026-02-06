package test

class Test
val nullNullable: Test? = null

fun foo(a: Test) {}

fun bar() {
    foo(nul<caret>)
}

// ORDER: nullNullable
// ORDER: null
// this works for K2 but null is even further down
// IGNORE_K2