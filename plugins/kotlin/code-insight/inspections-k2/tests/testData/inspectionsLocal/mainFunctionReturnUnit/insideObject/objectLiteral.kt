// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: JVM_STATIC_NOT_IN_OBJECT_OR_COMPANION
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

fun foo() {
    object {
        @JvmStatic
        fun main(args: List<String>): <caret>String {}
    }
}