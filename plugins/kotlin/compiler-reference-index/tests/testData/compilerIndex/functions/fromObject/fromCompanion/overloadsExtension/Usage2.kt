import Main.Companion.overloadsExtension

class Bar2 {
    fun Main.test() {
        with(42) {
            overloadsExtension("")
        }
    }
}