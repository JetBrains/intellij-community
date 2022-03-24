// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun foo() {
    val a = true
    val b = false
    <caret>if (a && b) {
        doSomething("test")
    }
}