package one.two

object KotlinObject {
    object Nested {
        @JvmStatic
        var Int.staticExtension<caret>Variable
            get() = 42
            set(value) {}
    }
}
