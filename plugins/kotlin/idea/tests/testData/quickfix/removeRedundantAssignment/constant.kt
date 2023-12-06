// "Remove redundant assignment" "true"
fun test() {
    var i: Int
    <caret>i = 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedValueFix