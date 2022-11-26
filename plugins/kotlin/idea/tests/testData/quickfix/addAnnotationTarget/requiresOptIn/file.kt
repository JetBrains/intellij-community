// "Add annotation target" "false"
// ACTION: Introduce import alias
// WITH_STDLIB
// DISABLE-ERRORS
@file:MyExperimentalAPI<caret>

@RequiresOptIn
@Target(AnnotationTarget.CLASS)
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some