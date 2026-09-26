// PROBLEM: Suspicious cascading 'if' expression
// FIX: Replace 'if' with 'when' (changes semantics)

fun test() {
    i<caret>f (true) {
        null
    } else if (true) {
        Any()
    } else {
        null
    } ?: Any()
}