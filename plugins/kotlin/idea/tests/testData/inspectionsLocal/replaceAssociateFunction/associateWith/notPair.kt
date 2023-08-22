// PROBLEM: none
// WITH_STDLIB
fun getValue(i: Int): String = ""

fun test(b: Boolean) {
    listOf(1).<caret>associate {
        if (b) {
            it to getValue(it)
        } else {
            it to ""
        }
    }
}