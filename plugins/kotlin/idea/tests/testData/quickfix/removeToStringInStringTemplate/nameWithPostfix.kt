// "Remove 'toString()' call" "true"

fun foo(arg: Any) = "arg = ${arg.<caret>toString()}xy"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFix$1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveToStringFix