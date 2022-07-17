// "Add annotation target" "true"

@Target(AnnotationTarget.CLASS)
expect annotation class Ann()

@Ann<caret>
val commonProperty = 42
