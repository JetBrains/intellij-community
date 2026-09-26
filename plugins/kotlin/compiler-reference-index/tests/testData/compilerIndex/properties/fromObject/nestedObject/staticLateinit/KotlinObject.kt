package one.two

object KotlinObject {
    object Nested {
        @JvmStatic
        lateinit var staticLateinit<caret>: Nested
    }
}
