// "Add annotation target" "false"
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// WITH_STDLIB
// DISABLE-ERRORS
fun test() {
    @MyExperimentalAPI<caret>
    1 + 1
}

@RequiresOptIn
@Target(AnnotationTarget.FIELD)
annotation class MyExperimentalAPI