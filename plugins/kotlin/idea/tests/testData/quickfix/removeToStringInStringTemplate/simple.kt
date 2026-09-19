// "Remove 'toString()' call" "true"

fun foo(s: String) = s

fun bar() = foo("a${"b".toString()<caret>}")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFix$1