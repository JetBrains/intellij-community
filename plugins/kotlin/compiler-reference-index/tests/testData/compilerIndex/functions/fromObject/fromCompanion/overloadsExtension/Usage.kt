import Main.Companion.overloadsExtension

class Bar {
    fun Main.test() {
        with(42) {
            overloadsExtension()
        }
    }
}