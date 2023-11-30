// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'" "true"
// ACTION: Add '-opt-in=BasicFunctionContainingClass.MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicFunctionContainingClass.kts'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

class Bar {
    fun bar() {
        foo<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix