fun test(lambda: () -> Unit) {

}

fun a() {
    test(
        {<caret>},
    )
}