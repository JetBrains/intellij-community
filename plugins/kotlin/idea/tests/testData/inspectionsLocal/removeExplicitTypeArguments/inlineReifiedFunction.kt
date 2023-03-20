// PROBLEM: none
inline fun <reified T> foo(x: T) {}

fun main() {
    foo<caret><Int>(42)
}