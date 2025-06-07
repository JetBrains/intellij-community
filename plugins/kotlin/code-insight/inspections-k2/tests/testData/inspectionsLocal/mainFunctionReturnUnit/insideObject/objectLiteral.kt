// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Missing return statement.
// K2_ERROR: Only members in named objects and companion objects can be annotated with '@JvmStatic'.

fun foo() {
    object {
        @JvmStatic
        fun main(args: List<String>): <caret>String {}
    }
}