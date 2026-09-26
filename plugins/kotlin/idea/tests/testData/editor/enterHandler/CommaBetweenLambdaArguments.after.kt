fun test(a: () -> Unit, b: () -> Unit) {}

fun usage() {
    test(
        a = { TODO()
        <caret>},
        b = { TODO() },
    )
}
