import Main.Companion.overloadsStaticExtension

class ClassUsage2 {
    fun Main.test() {
        with(42) {
            overloadsStaticExtension("awd")
        }
    }
}