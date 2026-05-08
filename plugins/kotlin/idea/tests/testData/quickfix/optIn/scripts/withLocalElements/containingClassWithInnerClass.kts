// "Opt in for 'Library' on statement" "true"
// PRIORITY: HIGH
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// ACTION: Opt in for 'Library' in containing file 'containingClassWithInnerClass.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 't5'
// ACTION: Opt in for 'Library' on containing class 'T4'
// ACTION: Opt in for 'Library' on containing class 'T5'
// ACTION: Opt in for 'Library' on statement
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@Library' or '@OptIn(Library::class)'
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    class T4() {
        inner class T5() {
            fun t5() {
                foo<caret>
            }
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
