// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Outer'" "false"
// IGNORE_K2
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Add '-opt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Introduce import alias
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'nestedClasses.kt'
// ACTION: Opt in for 'MyExperimentalAPI' on 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' on containing class 'Inner'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Inner'
// ERROR: This declaration needs opt-in. Its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

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
