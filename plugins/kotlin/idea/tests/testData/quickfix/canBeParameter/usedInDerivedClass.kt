// "Remove 'val' from parameter" "true"
open class Base(open <caret>val x: Int) {
    val y = x
}

class Derived(y: Int) : Base(y)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.CanBeParameterInspection$RemoveValVarFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RemoveValVarFix