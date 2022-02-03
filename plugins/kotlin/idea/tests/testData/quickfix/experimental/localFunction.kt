// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_RUNTIME

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

fun outer() {
    fun bar() {
        foo<caret>()
    }
}
