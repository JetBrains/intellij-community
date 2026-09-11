// "Remove 'toString()' call" "true"

fun foo(arg: Any) = "arg = ${arg.<caret>toString()}"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFix$1