// "Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'" "false"
// IGNORE_K2
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Add '-opt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicFunctionNotApplicable.kt'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
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
