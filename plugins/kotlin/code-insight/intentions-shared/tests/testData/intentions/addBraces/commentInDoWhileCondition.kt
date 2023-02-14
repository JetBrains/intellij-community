fun bar() {}

fun foo(x: Int) {
    <caret>do bar() while (x > 0 &&
        // some comment
        x < 5
    )
}