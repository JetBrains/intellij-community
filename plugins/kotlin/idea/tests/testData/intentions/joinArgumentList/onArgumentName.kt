fun foo(bar: Int, baz: Int) = bar + baz

fun main() {
    foo(
        b<caret>ar = 1111,
        baz = 2222
    )
}