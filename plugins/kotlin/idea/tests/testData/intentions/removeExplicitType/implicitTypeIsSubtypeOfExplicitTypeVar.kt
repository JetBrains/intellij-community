// IS_APPLICABLE: false
// AFTER-WARNING: Variable 'i' is never used
// IGNORE_K1
fun test() {
    var i: <caret>Any = 1
    i = ""
}