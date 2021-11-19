// WITH_STDLIB
fun foo(s: String): Int =
    <caret>with("s") {
        return 42
    }