import Main.Companion.overloadsStaticExtension

class ClassUsage {
    fun Main.test() {
        with(42) {
            overloadsStaticExtension()
        }
    }
}