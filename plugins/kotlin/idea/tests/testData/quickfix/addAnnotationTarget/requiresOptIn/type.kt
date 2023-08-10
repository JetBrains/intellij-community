// "Add annotation target" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce import alias
// WITH_STDLIB
// DISABLE-ERRORS
val x: @MyExperimentalAPI<caret> Int = 1

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI