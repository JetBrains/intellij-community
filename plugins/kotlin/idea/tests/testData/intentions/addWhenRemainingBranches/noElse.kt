// WITH_STDLIB
// ERROR: 'when' expression must be exhaustive, add necessary 'BAR', 'BAZ' branches or 'else' branch instead
// SKIP_ERRORS_AFTER

enum class Entry {
    FOO, BAR, BAZ
}

fun test(e: Entry) {
    <caret>when (e) {
        Entry.FOO -> {}
    }
}