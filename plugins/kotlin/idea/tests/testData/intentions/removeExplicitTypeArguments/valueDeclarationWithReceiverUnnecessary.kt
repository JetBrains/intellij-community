// FIX: Remove explicit type arguments
// AFTER-WARNING: Variable 'x' is never used
// AFTER-WARNING: Variable 'y' is never used

fun <T> T.ext(): T = this

fun String.testMeToo() {
    fun Int.testOther() {
        val x = ext<caret><Int>()
        val y = ext<String>()
    }
}
