package one.two

object KotlinObject {
    object Nested {
        var Int.extension<caret>Variable
            get() = 42
            set(value) {}
    }
}
