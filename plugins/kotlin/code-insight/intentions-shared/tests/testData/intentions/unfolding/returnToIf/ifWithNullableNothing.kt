// WITH_STDLIB
fun test(b: Boolean): Int? {
    <caret>return if (b) {
        1
    } else {
        getNull()
    }
}

private fun getNull(): Nothing? = null