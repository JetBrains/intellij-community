// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(vararg foo: Int, bar: Int) = foo + bar

fun main() {
    foo(<caret>foo = intArrayOf(1, 2), 1)
}