// "Remove forbidden opt-in annotation retention" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
@RequiresOptIn
<caret>@Retention(AnnotationRetention.SOURCE)
annotation class SomeAnnotation