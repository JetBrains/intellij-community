// "Add annotation target" "true"
@Target
annotation class PropertyAnn

class Property(<caret>@property:PropertyAnn val foo: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix