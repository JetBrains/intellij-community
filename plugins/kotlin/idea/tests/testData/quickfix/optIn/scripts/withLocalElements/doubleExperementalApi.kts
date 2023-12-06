// "Opt in for 'Library' on statement" "true"
// ACTION: Add '-opt-in=DoubleExperementalApi.Library' to module light_idea_test_case compiler arguments
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Do not show return expression hints
// ACTION: Introduce local variable
// ACTION: Opt in for 'Library' in containing file 'doubleExperementalApi.kts'
// ACTION: Opt in for 'Library' on 'bar'
// ACTION: Opt in for 'Library' on statement
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary{
    fun bar() = ""
}


@Library
val foo: MockLibrary = MockLibrary();

{
    fun bar() {
        foo.bar<caret>()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix