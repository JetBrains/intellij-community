// IS_APPLICABLE: false

object A {
    object B {
        fun foo() {}
    }
}

fun main() {
    <caret>A.B.foo()
}
