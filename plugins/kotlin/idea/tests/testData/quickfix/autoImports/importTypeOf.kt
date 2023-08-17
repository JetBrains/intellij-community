// "Import function 'typeOf'" "true"
// WITH_STDLIB

fun test() {
    <caret>typeOf<Int>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ImportQuickFix