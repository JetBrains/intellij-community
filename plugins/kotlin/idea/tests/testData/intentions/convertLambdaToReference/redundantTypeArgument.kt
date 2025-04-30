// IGNORE_K1
fun callMe(s: String) {}

fun <T> body(t: T, receiver: (String) -> Unit) {}

fun bar() {
    body<String>("") {
        <caret>callMe("")
    }
}

