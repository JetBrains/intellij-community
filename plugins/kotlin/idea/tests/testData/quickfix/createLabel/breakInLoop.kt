// "Create Label 'foo'@" "true"
// K2_ERROR: NOT_A_LOOP_LABEL

fun test() {
    while (true) {
        break@<caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop