// "Remove redundant 'final' modifier" "true"
open class C {
    <caret>final fun foo(){}
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantModalityModifierInspection$createQuickFixes$1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix