// "Create Label 'foo'@" "true"
// K2_ERROR: UNRESOLVED_LABEL

inline fun Int.bar(f: (Int) -> Unit) { }

fun test() {
    1.bar { 2.bar { if (it == 2) return@<caret>foo } }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLambda