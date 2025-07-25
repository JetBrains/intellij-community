// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Outer'" "false"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Introduce import alias
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'nestedClassesFir.kt'
// ACTION: Opt in for 'MyExperimentalAPI' in module 'light_idea_test_case'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Inner'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Inner'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

@RequiresOptIn
annotation class MyExperimentalAPI

@MyExperimentalAPI
fun foo() {
}

class Outer {
    class Bar {
        class Inner {
            fun bar() {
                foo<caret>()
            }
        }
    }
}
