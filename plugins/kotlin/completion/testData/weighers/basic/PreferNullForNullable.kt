package test

class Test
val nullNullableUnrelated: Any? = null
val nullNonNullableUnrelated: Int = 5

fun foo(a: Test?) {}

fun bar() {
    foo(nul<caret>)
}

// ORDER: null
// ORDER: nullNonNullableUnrelated
// ORDER: nullNullableUnrelated