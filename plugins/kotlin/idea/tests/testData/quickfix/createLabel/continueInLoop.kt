// "Create Label 'foo'@" "true"
// K2_ERROR: Label does not denote a reachable loop.

fun test() {
    while (true) {
        continue@<caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop