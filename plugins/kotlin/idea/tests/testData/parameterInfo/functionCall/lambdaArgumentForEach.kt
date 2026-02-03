// See: KTIJ-23373

// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).forEach { <caret> }
}
