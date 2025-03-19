// "Add annotation target" "true"
// IGNORE_K2

@Target(AnnotationTarget.CLASS)
actual annotation class Ann actual constructor()

@Ann<caret>
val jvmProperty = 42
