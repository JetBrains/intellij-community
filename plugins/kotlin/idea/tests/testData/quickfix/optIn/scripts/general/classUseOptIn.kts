// "Opt in for 'MyExperimentalAPI' on containing class 'Bar'" "true"
// PRIORITY: HIGH
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'classUseOptIn.kts'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@a.b.MyExperimentalAPI' or '@OptIn(a.b.MyExperimentalAPI::class)'

package a.b

@RequiresOptIn
@Target(AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

class Bar {
    fun bar() {
        foo<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix