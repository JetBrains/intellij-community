// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
@Target(AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

open class Base {
    @MyExperimentalAPI
    open fun foo() {}
}

class Outer {
    fun outer() {
        class Derived : Base() {
            override fun foo<caret>() {}
        }
    }
}
