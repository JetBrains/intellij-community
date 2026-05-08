// "Opt in for 'Library' on statement" "true"
// PRIORITY: HIGH
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Introduce local variable
// ACTION: Iterate over 'String'
// ACTION: Opt in for 'Library' in containing file 'doubleExperementalApi.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'bar'
// ACTION: Opt in for 'Library' on statement
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@Library' or '@OptIn(Library::class)'
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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
