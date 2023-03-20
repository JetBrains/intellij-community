package one.two

object KotlinObject {
    object Nested {
        @JvmStatic
        val Int.staticExt<caret>ension get() = 42
    }
}
