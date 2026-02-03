// "Add annotation target" "true"
@Target
annotation class ParamAnn

class Param(<caret>@param:ParamAnn val foo: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix