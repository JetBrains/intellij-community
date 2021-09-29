// WITH_RUNTIME
// AFTER-WARNING: Variable 'list' is never used
fun foo() {
    var list: ArrayList<Int> = <caret>ArrayList()
}