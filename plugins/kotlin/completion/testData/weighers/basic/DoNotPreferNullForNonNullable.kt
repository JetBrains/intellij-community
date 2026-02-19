package test

class Test
val nullNotNull: Test = Test()

fun foo(a: Test) {}

fun bar() {
    foo(nul<caret>)
}

// ORDER: nullNotNull
// ORDER: null
// this works for K2 but null is even further down
// IGNORE_K2