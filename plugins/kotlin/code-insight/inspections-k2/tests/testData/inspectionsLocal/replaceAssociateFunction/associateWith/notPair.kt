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

// This is a valid example of the applicability of an 'associateWith' conversion. We do not currently support it
// because of the complexity of the implementation and the high number of potential false positive/false negative scenarios.