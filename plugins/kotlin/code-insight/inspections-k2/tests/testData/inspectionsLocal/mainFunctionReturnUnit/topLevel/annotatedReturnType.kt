// K2-ERROR: Missing return statement.
fun main(): @Anno <caret>String {}

@Target(AnnotationTarget.TYPE)
annotation class Anno