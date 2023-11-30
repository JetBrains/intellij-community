// PROBLEM: none
class Bar {
    private fun <caret>m() {}

    fun test() {
        val f = ::m
    }
}