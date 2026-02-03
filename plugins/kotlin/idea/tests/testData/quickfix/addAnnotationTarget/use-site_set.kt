// "Add annotation target" "true"
@Target
annotation class SetAnn

class Set(<caret>@set:SetAnn var foo: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix