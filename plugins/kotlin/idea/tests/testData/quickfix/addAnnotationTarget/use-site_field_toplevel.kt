// "Add annotation target" "true"

@Target
annotation class Ann

@field:Ann<caret>
var foo = ""
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix