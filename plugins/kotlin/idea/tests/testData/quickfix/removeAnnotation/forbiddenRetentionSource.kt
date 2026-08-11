// "Remove forbidden opt-in annotation retention" "true"
// K2_ERROR: OPT_IN_MARKER_WITH_WRONG_RETENTION
@RequiresOptIn
<caret>@Retention(AnnotationRetention.SOURCE)
annotation class SomeAnnotation
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix