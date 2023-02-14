// "Add annotation target" "true"

@Target(AnnotationTarget.CLASS)
actual annotation class Ann actual constructor()

@Ann<caret>
val jvmProperty = 42
