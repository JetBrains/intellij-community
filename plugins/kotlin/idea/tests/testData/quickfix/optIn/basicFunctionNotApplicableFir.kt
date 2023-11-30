// "Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'" "false"
// IGNORE_K1
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicFunctionNotApplicableFir.kt'
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
