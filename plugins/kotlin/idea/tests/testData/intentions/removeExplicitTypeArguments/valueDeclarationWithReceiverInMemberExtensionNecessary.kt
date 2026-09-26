// PROBLEM: none
// IS_APPLICABLE: false

fun <T> T.ext(): T = this

class Main {
    fun Int.test() {
        val x = ext<Int>()
        val y = ext<caret><Main>()
    }
}
