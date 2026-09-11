// "Remove 'val' from parameter" "true"

class Wrapper(vararg <caret>val x: Int) {
    val y = x
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveValVarFix