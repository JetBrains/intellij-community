fun foo(a: Int = 1, b: Int = 2) {}

const val CONSTANT = 2

fun test() {
    foo(1, CONSTANT<caret>)
}