// OPTION: 2
fun test() {
    foo {
        <caret>val x = 1
    }
}

fun foo(body: () -> Unit) {}