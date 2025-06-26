// PROBLEM: none
// IS_APPLICABLE: false
// IGNORE_K1
fun <T> T.ext(): T = this

fun String.testMeToo() {
    fun Int.testOther() {
        val x = ext<caret><Int>()
        val y = ext<String>()
    }
}
