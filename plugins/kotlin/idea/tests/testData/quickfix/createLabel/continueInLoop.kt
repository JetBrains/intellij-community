// "Create label foo@" "true"

fun test() {
    while (true) {
        continue@<caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLoop