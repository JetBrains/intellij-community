// PROBLEM: Suspicious cascading 'if' expression
// FIX: Replace 'if' with 'when' (changes semantics)

fun test() {
    if (true) {
    } else {
        <caret>if (true) {
            1
        } else if (true) {
            2
        } else {
            3
        } + 4
    }
}
