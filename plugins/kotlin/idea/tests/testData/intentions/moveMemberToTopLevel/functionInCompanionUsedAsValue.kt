// WITH_STDLIB

val companion: Any = A

class A {
    companion object {
        fun <caret>foo() = 1
    }
}