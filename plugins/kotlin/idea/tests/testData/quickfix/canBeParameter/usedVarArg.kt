// "Remove 'val' from parameter" "true"

class Wrapper(vararg <caret>val x: Int) {
    val y = x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.CanBeParameterInspection$RemoveValVarFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RemoveValVarFix