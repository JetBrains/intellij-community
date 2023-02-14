import Main.Companion.staticExtension

fun test() {
    with(Main) {
        42.staticExtension()
    }
}