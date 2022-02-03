// "Propagate '@MyExperimentalAPI' annotation to containing class 'Outer'" "false"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// WITH_STDLIB
// ACTION: Propagate '@MyExperimentalAPI' annotation to 'bar'
// ACTION: Propagate '@MyExperimentalAPI' annotation to containing class 'Inner'
// ACTION: Opt-in for 'MyExperimentalAPI::class' on 'bar'
// ACTION: Opt-in for 'MyExperimentalAPI::class' on containing class 'Inner'
// ACTION: Opt-in for 'MyExperimentalAPI::class' on containing file 'nestedClasses.kt'
// ACTION: Add '-Xopt-in=MyExperimentalAPI' to module light_idea_test_case compiler arguments
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
