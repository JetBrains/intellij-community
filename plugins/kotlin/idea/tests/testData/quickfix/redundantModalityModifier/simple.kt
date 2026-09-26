// "Remove redundant 'final' modifier" "true"
open class C {
    <caret>final fun foo(){}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.RedundantModalityModifierInspection$createQuickFix$1