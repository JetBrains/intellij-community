fun test(lambda: () -> Unit) {

}

fun a() {
    test(
        lambda = {
            <caret>
        },
    )
}
