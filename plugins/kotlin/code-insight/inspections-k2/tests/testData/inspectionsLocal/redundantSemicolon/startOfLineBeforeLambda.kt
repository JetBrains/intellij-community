// PROBLEM: none
fun foo(): () -> Unit = if (true) {
    {}
} else {
    bar()
    <caret>;{}
}

fun bar() {}