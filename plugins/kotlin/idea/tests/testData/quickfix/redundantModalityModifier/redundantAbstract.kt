// "Remove redundant 'abstract' modifier" "true"
interface A {
    <caret>abstract fun foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.RedundantModalityModifierInspection$createQuickFix$1