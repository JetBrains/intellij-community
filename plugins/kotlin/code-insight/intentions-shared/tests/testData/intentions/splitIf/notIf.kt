// IS_APPLICABLE: false
// AFTER-WARNING: The expression is unused
fun <T> doSomething(a: T) {}

fun foo() {
    val a = true
    val b = false
    when (<caret>a && b) {
        else -> doSomething("test")
    }
}
