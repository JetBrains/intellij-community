// IS_APPLICABLE: false
// AFTER-WARNING: Variable 'i' is never used

fun test() {
    var i: <caret>Any = 1
    i = ""
}