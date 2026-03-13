// "Replace ',' with '||' in when" "true"
// K2_ERROR: Use '||' instead of commas in conditions of 'when' without a subject.
// K2_ERROR: Use '||' instead of commas in conditions of 'when' without a subject.
fun test(i: Int, j: Int) {
    var b = false
    when {
        i == 0 -> { /* code 1 */ }
        i > 0<caret>, j > 0 -> { /* code 2 */ }
        j == 0 -> { /* code 3 */ }
        i < 0, j < 0, j > i -> { /* code 4 */ }
        else -> { /* other code */ }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CommaInWhenConditionWithoutArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CommaInWhenConditionWithoutArgumentFix