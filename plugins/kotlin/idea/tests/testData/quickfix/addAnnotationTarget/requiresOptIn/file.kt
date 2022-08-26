// "Add annotation target" "false"
// ACTION: Introduce import alias
// WITH_STDLIB
// DISABLE-ERRORS
@file:MyExperimentalAPI<caret>

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI