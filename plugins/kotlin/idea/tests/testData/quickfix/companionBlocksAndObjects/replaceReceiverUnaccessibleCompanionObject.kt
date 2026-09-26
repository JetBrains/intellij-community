// "Replace the receiver with 'Example'" "false"
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
class Example {
    companion object {
        private fun test() {}
    }
}

fun m(e: Example) {
    e.te<caret>st()
}
