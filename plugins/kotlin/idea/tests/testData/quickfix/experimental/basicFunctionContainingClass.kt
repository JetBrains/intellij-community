// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'" "true"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

class Bar {
    fun bar() {
        foo<caret>()
    }
}
