// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// ACTION: Introduce import alias
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'localFunction.kts'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on 'outer'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

fun outer() {
    fun bar() {
        foo<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix