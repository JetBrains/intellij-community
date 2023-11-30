// "Add '-opt-in=test.BasicModule.MyExperimentalAPI' to module light_idea_test_case compiler arguments" "true"
// PRIORITY: LOW
// ACTION: Add '-opt-in=test.BasicModule.MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicModule.kts'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// COMPILER_ARGUMENTS_AFTER:-opt-in=test.BasicModule.MyExperimentalAPI
// DISABLE-ERRORS
// RUNTIME_WITH_SCRIPT_RUNTIME

package test

@RequiresOptIn
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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFileLevelFixesFactory$LowPriorityMakeModuleOptInFix