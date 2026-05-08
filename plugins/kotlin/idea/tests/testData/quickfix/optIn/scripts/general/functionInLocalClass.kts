// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// PRIORITY: HIGH
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Go To Super Method
// ACTION: Move to top level
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'functionInLocalClass.kts'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'foo'
// ACTION: Opt in for 'MyExperimentalAPI' on 'outer'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Derived'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Outer'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: Base declaration of supertype 'Base' needs opt-in. The declaration override must be annotated with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix