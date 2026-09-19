// "Remove 'toString()' call" "true"

val foo = "test"
val bar = "${foo.toString()<caret>}_"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFix$1