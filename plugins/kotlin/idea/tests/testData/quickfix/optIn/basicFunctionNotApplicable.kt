// "Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'" "false"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicFunctionNotApplicable.kt'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
// ERROR: This annotation is not applicable to target 'member function'
// K2_AFTER_ERROR: This annotation is not applicable to target 'member function'. Applicable targets: class
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

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
