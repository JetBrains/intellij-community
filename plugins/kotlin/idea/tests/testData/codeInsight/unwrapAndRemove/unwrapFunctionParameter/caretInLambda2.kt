// OPTION: 2
fun test() {
    foo(1) {
        <caret>val x = 1
    }
}

fun foo(a: Int, body: () -> Unit) {}