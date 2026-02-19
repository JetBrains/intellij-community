fun <T, V> foo(t: T): T = t
fun <T, V> foo(t: T, p: Int): T = t
fun <X> foo(p: Boolean, x: X) {}

fun bar() {
    foo<<caret>>
}
