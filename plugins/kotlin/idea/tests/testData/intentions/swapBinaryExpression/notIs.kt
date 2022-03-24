// IS_APPLICABLE: false
// AFTER-WARNING: Check for instance is always 'false'
// AFTER-WARNING: Parameter 'a' is never used
fun <T> doSomething(a: T) {}

fun main(x: Int) {
    if (x <caret>!is Int) {
        doSomething("test")
    }
}
