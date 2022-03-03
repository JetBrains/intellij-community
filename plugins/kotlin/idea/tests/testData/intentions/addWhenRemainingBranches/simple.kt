// WITH_STDLIB
// AFTER-WARNING: 'when' is exhaustive so 'else' is redundant here
enum class Entry {
    FOO, BAR, BAZ
}

fun test(e: Entry) {
    <caret>when (e) {
        Entry.FOO -> {}
        else -> {}
    }
}