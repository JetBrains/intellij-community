// PROBLEM: none

@Target(AnnotationTarget.TYPE)
annotation class Ann

fun foo() {
    val t: @Ann <caret>Boolean = true
}
