// FIX: Remove redundant underscore entry (may change semantics)
// PROBLEM: Underscore entry is redundant
// HIGHLIGHT: NOT_USED_ELEMENT_ATTRIBUTES
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

class Foo {
    @Volatile
    var title: String = "t"
    val n: Int = 1
}

fun test(x: Foo) {
    (val <caret>_ = title, val n) = x
}
