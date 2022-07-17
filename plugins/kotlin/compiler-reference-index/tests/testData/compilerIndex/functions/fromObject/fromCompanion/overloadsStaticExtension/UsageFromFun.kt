import Main.Companion.overloadsStaticExtension

fun test() {
    with(Main) {
        42.overloadsStaticExtension()
    }
}