fun bar() {}

fun foo(x: Int) {
    when {
        x > 0 &&
                // some comment
                x < 5 -><caret> bar()
    }
}