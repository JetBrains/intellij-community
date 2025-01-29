// "Remove 'toString()' call" "true"

operator fun Any.invoke() = this

fun foo(arg: Any) = "${arg().<caret>toString()}"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFixes$1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveToStringFix