// IS_APPLICABLE: false
// WITH_STDLIB
fun main() {
    Any().bar <caret>{ it.remove<Int>() }
}

fun Any.bar(action: (Any) -> Unit) {
    TODO()
}

fun <T> Any.remove(): Int = TODO()
fun Any.remove(): Unit = TODO()
