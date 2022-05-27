// WITH_STDLIB
// SKIP_ERRORS_BEFORE

enum class Entry {
    FOO, BAR, BAZ
}

fun test(e: Entry) {
    <caret>when (e) {
        Entry.FOO -> {}
    }
}