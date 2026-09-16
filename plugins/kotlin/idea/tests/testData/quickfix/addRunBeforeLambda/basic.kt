// "Add 'run' before the lambda expression" "true"
// WITH_STDLIB

fun foo() {
    {}<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedLambdaExpressionInspection$createQuickFix$1