import Main.Companion.staticExtension

class ClassUsage {
    fun Main.test() {
        with(42) {
            staticExtension()
        }
    }
}