// "Remove 'var' from parameter" "true"
class UsedInProperty(<caret>var x: Int) {
    var y = x
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.CanBeParameterInspection$RemoveValVarFix