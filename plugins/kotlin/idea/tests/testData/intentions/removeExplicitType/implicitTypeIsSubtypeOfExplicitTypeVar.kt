// IS_APPLICABLE: false
// AFTER-WARNING: Variable 'i' is never used
// IGNORE_FE10
fun test() {
    var i: <caret>Any = 1
    i = ""
}