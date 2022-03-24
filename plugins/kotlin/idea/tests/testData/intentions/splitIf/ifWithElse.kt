// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun foo() {
    val a = true
    val b = false
    if (a <caret>&& b) {
        doSomething("test")
    } else {
        doSomething("test2")
    }
}
