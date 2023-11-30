// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Derived'" "false"
// IGNORE_K2
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Add '-opt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Go To Super Method
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'override.kt'
// ACTION: Opt in for 'MyExperimentalAPI' on 'foo'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Derived'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'foo'
// ERROR: Base declaration of supertype 'Base' needs opt-in. The declaration override must be annotated with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

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
