// "Remove forbidden opt-in annotation retention" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
@RequiresOptIn
<caret>@Retention(AnnotationRetention.SOURCE)
annotation class SomeAnnotation
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWrongOptInAnnotationRetentionFactory$RemoveForbiddenOptInRetentionFix