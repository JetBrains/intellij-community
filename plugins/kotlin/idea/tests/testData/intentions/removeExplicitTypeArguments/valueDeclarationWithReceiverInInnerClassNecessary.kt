// PROBLEM: none
// IS_APPLICABLE: false

fun <T> T.ext(): T = this

class Outer {
    inner class Inner {
        val outer = ext<caret><Outer>()
        val inner = ext<Inner>()
    }
}
