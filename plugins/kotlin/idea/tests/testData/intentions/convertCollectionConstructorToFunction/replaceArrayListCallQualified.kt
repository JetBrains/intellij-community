// WITH_RUNTIME
// AFTER-WARNING: Variable 'list' is never used
fun foo() {
    var list = java.util.<caret>ArrayList<Int>()
}