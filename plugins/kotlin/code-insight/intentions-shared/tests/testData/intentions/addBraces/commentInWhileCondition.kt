fun bar() {}

fun foo(x: Int) {
    while (x > 0 &&
        // some comment
        x < 5
    )<caret> bar()
}