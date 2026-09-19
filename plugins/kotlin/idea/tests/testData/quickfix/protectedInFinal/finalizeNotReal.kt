// "Make class open" "true"
class Op {
    <caret>protected fun finalize(): Int {return 1}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.ProtectedInFinalInspectionBase$MakeOpenFix