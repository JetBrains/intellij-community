// WITH_STDLIB

fun bar() = A.Companion.foo()

class A {
    companion object {
        fun <caret>foo() = 1
    }
}