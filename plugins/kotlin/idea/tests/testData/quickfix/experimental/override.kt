// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Derived'" "false"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'foo'
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'override.kt'
// ACTION: Opt in for 'MyExperimentalAPI' on 'foo'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Derived'
// ACTION: Add '-opt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Go To Super Method
// ERROR: This declaration overrides experimental member of supertype 'Base' and must be annotated with '@MyExperimentalAPI'

@RequiresOptIn
@Target(AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

open class Base {
    @MyExperimentalAPI
    open fun foo() {}
}

class Derived : Base() {
    override fun foo<caret>() {}
}
