// IS_APPLICABLE: false

interface A {
    class B {
        fun foo() {}
    }
}

fun main() {
    <caret>A.B().foo()
}
