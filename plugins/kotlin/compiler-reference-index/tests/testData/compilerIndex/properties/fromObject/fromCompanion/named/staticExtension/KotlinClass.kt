package one.two

class KotlinClass {
    companion object Named {
        @JvmStatic
        val Int.staticExt<caret>ension get() = 42
    }
}
