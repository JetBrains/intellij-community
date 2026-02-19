// PROBLEM: Suspicious cascading 'if' expression
// FIX: Replace 'if' with 'when' (changes semantics)
// IGNORE_K1
fun translateNumber(n: Int, a: Int): String {
    return if<caret> (a == 1) {
        "one"
    } else if (n == 2) {
        "two"
    } else if (n == 3) {
        "three"
    } else if (n == 4) {
        "four"
    } else {
        "???"
    } + 1
}