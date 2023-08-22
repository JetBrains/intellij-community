// "Remove redundant 'abstract' modifier" "true"
interface A {
    <caret>abstract fun foo()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RedundantModalityModifierInspection
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFix