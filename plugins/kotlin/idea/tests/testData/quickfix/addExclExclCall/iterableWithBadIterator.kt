// "Add non-null asserted (test!!) call" "false"
// ACTION: Surround with null check
// ERROR: Not nullable value required to call an 'iterator()' method on for-loop range

class Some {
    fun iterator(): Iterator<Int> = null!!
}

fun foo() {
    val test: Some? = Some()
    for (i in <caret>test) { }
}
