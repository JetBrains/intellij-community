// "Create Label 'foo bar'@" "true"
// K2_ERROR: UNRESOLVED_LABEL

inline fun Int.bar(f: (Int) -> Unit) { }

fun test() {
    1.bar { if (it == 2) return@`foo bar`<caret> }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLambda
