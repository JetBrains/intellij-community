// "Opt in for 'Library' on statement" "true"
// PRIORITY: HIGH
// ACTION: Introduce local variable
// ACTION: Opt in for 'Library' in containing file 'simple.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'bar'
// ACTION: Opt in for 'Library' on statement
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    fun bar() {
        foo<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix