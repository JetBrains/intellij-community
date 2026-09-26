// WITH_STDLIB
// PROBLEM: none

fun foo() {
    class Local {
        val local<caret> = java.lang.String.valueOf(3)
    }
}