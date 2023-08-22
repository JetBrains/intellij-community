// "Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'" "false"
// RUNTIME_WITH_SCRIPT_RUNTIME
// ACTION: Add '-opt-in=BasicFunctionNotApplicable.MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicFunctionNotApplicable.kts'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@BasicFunctionNotApplicable.MyExperimentalAPI' or '@OptIn(BasicFunctionNotApplicable.MyExperimentalAPI::class)'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@BasicFunctionNotApplicable.MyExperimentalAPI' or '@OptIn(BasicFunctionNotApplicable.MyExperimentalAPI::class)'
// ERROR: This annotation is not applicable to target 'member function'

@RequiresOptIn
@Target(AnnotationTarget.CLASS)
annotation class MyExperimentalAPI

@MyExperimentalAPI
class Some {
    @MyExperimentalAPI
    fun foo() {}
}

class Bar {
    fun bar() {
        Some().foo<caret>()
    }
}
