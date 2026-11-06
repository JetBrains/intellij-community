// IS_APPLICABLE: false
fun <T> doSomething(a: T) {}

fun main() {
    if (<caret>"test" is String) {
        doSomething("Hello")
    }
}
