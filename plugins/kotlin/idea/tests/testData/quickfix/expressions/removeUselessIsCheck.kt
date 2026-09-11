// "Remove redundant 'is' check" "true"
fun foo(a: String) {
    if (<caret>1 is Int) {

    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFix