// WITH_STDLIB
fun test(): Sequence<Int> {
    return sequenceOf(1, null, 2)
        // TEXT1
        // TEXT1
        // TEXT1
        .map<caret> {
            // TEXT2
            // TEXT2
            // TEXT2
            it
        }
        // TEXT3
        // TEXT3
        // TEXT3
        .filterNotNull() // Some Additional Comment
}