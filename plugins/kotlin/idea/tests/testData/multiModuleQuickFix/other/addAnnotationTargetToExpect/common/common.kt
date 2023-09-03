// "Add annotation target" "true"
// IGNORE_K2

@Target(AnnotationTarget.CLASS)
expect annotation class Ann()

@Ann<caret>
val commonProperty = 42
