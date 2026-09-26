// WITH_STDLIB
// FIX: Replace with 'toString' function (may change semantics)

fun foo() {
    val radix = 42
    val t = java.lang.Long.<caret>toString(5, radix) + 6
}
