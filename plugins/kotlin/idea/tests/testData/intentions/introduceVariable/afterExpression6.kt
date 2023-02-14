// IS_APPLICABLE: false
fun foo() = 0

fun test() {
    foo()
    <caret>val x = 1
}