// WITH_STDLIB

fun test(cond: Boolean): String {
    <caret>return if (cond) {
        "Hello"
    } else {
        run { error("This is bad") }
    }
}
