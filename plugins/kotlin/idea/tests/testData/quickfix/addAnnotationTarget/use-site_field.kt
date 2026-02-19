// "Add annotation target" "true"
@Target
annotation class FieldAnn

class Field(<caret>@field:FieldAnn val foo: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix