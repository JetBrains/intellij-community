import Main.Companion.overloadsExtension

fun test() {
    with(Main) {
        42.overloadsExtension()
    }
}