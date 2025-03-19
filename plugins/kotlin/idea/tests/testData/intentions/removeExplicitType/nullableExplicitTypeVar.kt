// IS_APPLICABLE: false
// IGNORE_K1
// AFTER-WARNING: Variable 'i' is never used
fun test() {
    var i: <caret>Int? = 1
    i = null
}