// "Add annotation target" "true"
// WITH_STDLIB
@Target
annotation class DelegateAnn

<caret>@delegate:DelegateAnn
val foo by lazy { "" }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix