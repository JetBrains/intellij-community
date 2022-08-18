package one.two

object KotlinObject {
    object NestedObject {
        @JvmOverloads
        fun overloadsFunction<caret>(i: Int = 3, s: String = "as") {

        }
    }
}