// WITH_STDLIB
// PROBLEM: none
// K2-ERROR: Missing return statement.

object Foo {
    @JvmStatic
    fun main(): <caret>Int {}
}