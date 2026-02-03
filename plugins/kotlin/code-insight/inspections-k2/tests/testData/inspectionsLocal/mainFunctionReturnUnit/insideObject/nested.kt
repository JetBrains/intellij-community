// WITH_STDLIB
// K2_ERROR: Missing return statement.

object Foo {
    object Bar {
        @JvmStatic
        fun main(args: Array<String>): <caret>String {}
    }
}