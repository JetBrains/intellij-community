// "Replace with 'anotherMethod(a, block)'" "true"

fun <T> anotherMethod(a: T, block: () -> Unit) {}

@Deprecated("Use anotherMethod instead", ReplaceWith("anotherMethod(a, block)"))
fun <T> aMethod(a: T, block: () -> Unit) {
}

fun main() {
    <caret>aMethod(1) {}
}
