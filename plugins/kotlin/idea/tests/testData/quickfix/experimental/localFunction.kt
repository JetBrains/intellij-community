// "Propagate '@MyExperimentalAPI' annotation to 'outer'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

fun outer() {
    fun bar() {
        foo<caret>()
    }
}
