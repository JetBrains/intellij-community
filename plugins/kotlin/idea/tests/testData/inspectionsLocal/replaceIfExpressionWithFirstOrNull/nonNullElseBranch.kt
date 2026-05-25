// PROBLEM: none
// WITH_STDLIB

fun test(s: String): Char {
    return <caret>if (s.isNotEmpty()) s[0] else 'x'
}
