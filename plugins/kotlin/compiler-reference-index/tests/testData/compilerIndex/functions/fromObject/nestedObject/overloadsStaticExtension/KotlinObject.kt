package one.two

object KotlinObject {
    object NestedObject {
        @JvmStatic
        @JvmOverloads
        fun Receiver.overloadsStatic<caret>Extension(i: Int = 3) {

        }
    }
}

class Receiver