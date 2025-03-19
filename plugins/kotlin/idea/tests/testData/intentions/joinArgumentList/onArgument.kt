fun foo(bar: Int, baz: Int) = bar + baz

fun main() {
    foo(
        1111,
        22<caret>22
    )
}