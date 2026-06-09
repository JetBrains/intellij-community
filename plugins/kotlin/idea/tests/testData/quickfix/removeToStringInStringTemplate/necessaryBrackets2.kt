// "Remove 'toString()' call" "true"

val foo = "test"
val bar = "${foo.toString()<caret>}コトリン"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.RemoveToStringInStringTemplateInspection$createQuickFix$1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveToStringFix