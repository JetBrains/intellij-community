// "Remove 'toString()' call" "true"

val foo = "test"
val bar = "${foo.toString()<caret>}コトリン"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RemoveToStringInStringTemplateInspection
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveToStringFix