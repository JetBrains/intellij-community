/* IGNORE_K2 */
// Remove this ignoring after KT-69829 is fixed

// "Create label foo@" "true"

fun test() {
    while (true) {
        while (true) {
            break@<caret>foo
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop