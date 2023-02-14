// WITH_STDLIB
// FIX: Replace with 'repeat()'

fun test(s: String) {
    s.for<caret>Each {}
}