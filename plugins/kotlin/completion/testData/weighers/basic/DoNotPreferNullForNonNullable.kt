package test

class Test
val nullNotNull: Test = Test()

fun foo(a: Test) {}

fun bar() {
    foo(nul<caret>)
}

// ORDER: nullNotNull
// ORDER: null