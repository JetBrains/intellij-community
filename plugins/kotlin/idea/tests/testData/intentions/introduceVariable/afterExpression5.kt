// IS_APPLICABLE: false
fun foo() = 0

fun test() {
    foo()
    val lambda = { x: Int<caret> -> x + 2 }
}