// "Remove redundant initializer" "true"
// WITH_STDLIB
fun foo() {
    var bar = 1<caret>
    bar = 42
    println(bar)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.VariableInitializerIsRedundantInspection$createQuickFixes$1