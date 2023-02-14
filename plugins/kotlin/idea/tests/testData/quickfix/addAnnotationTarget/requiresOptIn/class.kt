// "Add annotation target" "true"
// WITH_STDLIB
// DISABLE-ERRORS
@file:MyExperimentalAPI

@MyExperimentalAPI<caret>
class Test

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI