// WITH_STDLIB
// PROBLEM: none

fun foo() {
    class Local {
        fun bar<caret>() = java.lang.String.valueOf(4)
    }
}