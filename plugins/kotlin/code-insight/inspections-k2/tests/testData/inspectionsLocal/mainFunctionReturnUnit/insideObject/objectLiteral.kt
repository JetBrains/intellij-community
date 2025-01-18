// WITH_STDLIB
// PROBLEM: none
// K2-ERROR: Missing return statement.
// K2-ERROR: Only members in named objects and companion objects can be annotated with '@JvmStatic'.

fun foo() {
    object {
        @JvmStatic
        fun main(args: List<String>): <caret>String {}
    }
}