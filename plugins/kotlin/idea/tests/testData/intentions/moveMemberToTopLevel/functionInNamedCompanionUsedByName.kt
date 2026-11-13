// WITH_STDLIB

val companion: A.Named = A.Named

class A {
    companion object Named {
        fun <caret>foo() = 1
    }
}