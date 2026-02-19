// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun foo() {
    <caret>while (true)
        doSomething("test")
}
