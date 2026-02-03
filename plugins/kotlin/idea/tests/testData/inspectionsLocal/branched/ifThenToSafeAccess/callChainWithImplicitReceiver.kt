// FIX: Replace 'if' expression with safe access expression
// HIGHLIGHT: INFORMATION
// IGNORE_K1
class My(val x: Int)

fun Any.foo(): Int? {
    return i<caret>f (this is My) x.hashCode() else null
}