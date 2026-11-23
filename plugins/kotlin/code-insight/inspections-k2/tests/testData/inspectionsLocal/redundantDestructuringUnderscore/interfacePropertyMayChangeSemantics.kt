// FIX: Remove redundant underscore entry (may change semantics)
// PROBLEM: Underscore entry is redundant
// HIGHLIGHT: NOT_USED_ELEMENT_ATTRIBUTES
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

interface Named {
    val title: String
    val n: Int
}

fun test(x: Named) {
    (val <caret>_ = title, val n) = x
}
