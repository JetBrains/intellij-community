import Main.Companion.invoke

fun Main.test3() {
    with(42) {
        invoke("")
    }
}