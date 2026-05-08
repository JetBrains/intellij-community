// "Opt in for 'Library' on containing object" "true"
// PRIORITY: HIGH
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce backing property
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// ACTION: Opt in for 'Library' in containing file 'containingObject.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'a'
// ACTION: Opt in for 'Library' on containing object
// RUNTIME_WITH_SCRIPT_RUNTIME
// K2_ERROR: This declaration needs opt-in. Its usage must be marked with '@Library' or '@OptIn(Library::class)'
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    object {
        val a = foo<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix
