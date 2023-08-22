package one.two

class KotlinClass {
    companion object Named {
        @JvmStatic
        var Int.staticExtension<caret>Variable
            get() = 42
            set(value) {}
    }
}
