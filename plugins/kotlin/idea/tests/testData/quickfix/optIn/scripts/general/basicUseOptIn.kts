// "Opt in for 'MyExperimentalAPI' on 'bar'" "true"
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'basicUseOptIn.kts'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME

package a.b

@RequiresOptIn
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
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

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix