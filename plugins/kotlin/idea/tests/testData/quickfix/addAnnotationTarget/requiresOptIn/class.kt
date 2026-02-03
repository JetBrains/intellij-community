// "Add annotation target" "true"
// WITH_STDLIB
// DISABLE_ERRORS
@file:MyExperimentalAPI

@MyExperimentalAPI<caret>
class Test

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix