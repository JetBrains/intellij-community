package one.two

object KotlinObject {
    object NestedObject {
        @JvmStatic
        fun Receiver.static<caret>Extension(i: Int) {

        }
    }
}

class Receiver