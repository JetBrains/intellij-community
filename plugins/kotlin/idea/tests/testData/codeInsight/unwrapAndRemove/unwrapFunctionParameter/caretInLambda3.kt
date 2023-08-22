// OPTION: 2
fun test() {
    foo(1, body = {
        <caret>val x = 1
    })
}

fun foo(a: Int, body: () -> Unit) {}