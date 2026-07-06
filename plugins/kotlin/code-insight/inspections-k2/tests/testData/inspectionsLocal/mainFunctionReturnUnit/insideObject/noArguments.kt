// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

object Foo {
    @JvmStatic
    fun main(): <caret>Int {}
}