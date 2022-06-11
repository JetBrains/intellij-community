fun bar() {}

fun foo(x: Int) {
    for (i in 1..x /* some comment */)<caret> bar()
}