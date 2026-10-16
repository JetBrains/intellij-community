// DISABLE_K2_ERRORS
// PROBLEM: none

private fun target<caret>(count: Int): Int = count + 2

fun caller() {
    target(1, 2, 3)
}
