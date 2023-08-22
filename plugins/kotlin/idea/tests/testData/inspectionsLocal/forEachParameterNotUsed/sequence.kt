// WITH_STDLIB
// FIX: Replace with 'repeat()'

fun test(sequence: Sequence<String>) {
    sequence.for<caret>Each {}
}