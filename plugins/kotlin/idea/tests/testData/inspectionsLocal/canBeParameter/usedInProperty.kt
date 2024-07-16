// FIX: Remove 'var' from parameter
class UsedInProperty(<caret>var x: Int) {
    var y = x
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.CanBeParameterInspection$RemoveValVarFix