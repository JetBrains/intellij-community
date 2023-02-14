// PROBLEM: 'also' has empty body
// FIX: none
// WITH_STDLIB

fun test(i: Int) {
    i.<caret>also {
        // comment
    }
}