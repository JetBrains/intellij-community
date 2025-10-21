// WITH_STDLIB
fun <T : Any> foo(value: T) {
    if (value == 0) {
        println()
    }
    println()
}

fun main() {
    foo(0)
}