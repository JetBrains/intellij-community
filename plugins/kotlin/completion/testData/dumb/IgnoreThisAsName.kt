// it should only show the keyword completion for this, and not show a value called this
fun main() {
    val a = this
    val b = thi<caret>
}

// EXIST: {"lookupString":"this","attributes":"bold","allLookupStrings":"this","itemText":"this"}
// NOTHING_ELSE