// PROBLEM: none
inline fun <T, reified U, V> foo(t: T, u: U, v: V) {}

fun main() {
    foo<caret><_, Int, _>(42, 42, 42)
}