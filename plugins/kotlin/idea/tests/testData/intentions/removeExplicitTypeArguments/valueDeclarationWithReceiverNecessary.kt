// PROBLEM: none
// IS_APPLICABLE: false

fun <T> T.ext(): T = this

fun String.testMeToo() {
    fun Int.testOther() {
        val x = ext<Int>()
        val y = ext<caret><String>()
    }
}
