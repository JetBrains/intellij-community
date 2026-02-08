// "Apply all 'Remove redundant calls of the conversion method' fixes in file" "true"

fun test() {
    "abc".to<caret>String()
    "def".toString()
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems