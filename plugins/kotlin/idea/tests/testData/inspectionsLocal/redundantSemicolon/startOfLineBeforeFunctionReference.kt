// WITH_RUNTIME
fun foo(): () -> Unit = if (true) {
    {}
} else {
    bar()
    <caret>;::println
}

fun bar() {}