inline fun <T> foo(x: T) {}

fun main() {
    foo<caret><Int>(42)
}