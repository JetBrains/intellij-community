// "Change to 'return@foo'" "true"
// K2_ERROR: Return type mismatch: expected 'Unit', actual 'Int'.
inline fun foo(f: (Int) -> Int) {}

fun test() {
    foo { i ->
        if (i == 1) return 1<caret>
        0
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix