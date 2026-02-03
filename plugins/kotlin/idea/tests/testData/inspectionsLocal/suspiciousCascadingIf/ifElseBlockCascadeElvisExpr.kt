// PROBLEM: Suspicious cascading 'if' expression
// FIX: Replace 'if' with 'when' (changes semantics)
// IGNORE_K1
fun test() {
    i<caret>f (true) {
        null
    } else if (true) {
        Any()
    } else {
        null
    } ?: Any()
}