// "Add annotation target" "true"

@Target(AnnotationTarget.FIELD)
annotation class FieldAnn

<caret>@FieldAnn
val x get() = 42
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix