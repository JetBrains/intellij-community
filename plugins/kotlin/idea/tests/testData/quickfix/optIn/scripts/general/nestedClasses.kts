// "Opt in for 'MyExperimentalAPI' on 'bar'" "true"
// PRIORITY: HIGH
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'nestedClasses.kts'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Inner'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Outer'
// ACTION: Opt in for 'MyExperimentalAPI' on statement
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Inner'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Outer'
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {}

class Outer {
    class Bar {
        class Inner {
            fun bar() {
                foo<caret>()
            }
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix