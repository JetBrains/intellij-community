// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Derived'" "false"
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add '-opt-in=Override.MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Go To Super Method
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'override.kts'
// ACTION: Opt in for 'MyExperimentalAPI' on 'foo'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Derived'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'foo'
// ERROR: Base declaration of supertype 'Base' needs opt-in. The declaration override must be annotated with '@Override.MyExperimentalAPI' or '@OptIn(Override.MyExperimentalAPI::class)'
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
