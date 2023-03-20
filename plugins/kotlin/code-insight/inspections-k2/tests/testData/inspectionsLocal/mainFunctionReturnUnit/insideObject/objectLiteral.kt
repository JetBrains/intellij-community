// WITH_STDLIB
// PROBLEM: none

fun foo() {
    object {
        @JvmStatic
        fun main(args: List<String>): <caret>String {}
    }
}