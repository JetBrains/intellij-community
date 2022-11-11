package one.two

object KotlinObject {
    object NestedObject {
        @JvmOverloads
        fun Receiver.<caret>overloadsExtension(i: Int = 4) {

        }
    }
}

class Receiver