// IS_APPLICABLE: false
fun foo() = 0

fun println() {}

fun test() {
    foo()
    <caret>val x = 1
}