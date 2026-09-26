fun foo(x: Int) {}

fun bar() {
    if (fal<caret>se) {
        foo(1)
        foo(2)
    }
}