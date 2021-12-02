// "Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Outer'" "false"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_RUNTIME
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to 'bar'
// ACTION: Propagate 'MyExperimentalAPI' opt-in requirement to containing class 'Inner'
// ACTION: Opt in for 'MyExperimentalAPI' in 'bar'
// ACTION: Opt in for 'MyExperimentalAPI' in containing class 'Inner'
// ACTION: Opt in for 'MyExperimentalAPI' in containing file 'nestedClasses.kt'
// ACTION: Add '-opt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
// ACTION: Introduce import alias
// ERROR: This declaration is experimental and its usage must be marked with '@MyExperimentalAPI' or '@OptIn(MyExperimentalAPI::class)'

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
