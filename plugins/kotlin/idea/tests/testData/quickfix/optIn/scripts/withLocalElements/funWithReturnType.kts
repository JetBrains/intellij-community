// "Opt in for 'Library' on 'bar'" "true"
// PRIORITY: HIGH
// ACTION: Add full qualifier
// ACTION: Convert to block body
// ACTION: Introduce import alias
// ACTION: Opt in for 'Library' in containing file 'funWithReturnType.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'bar'
// ACTION: Remove explicit type specification
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    fun bar(): MockLibrary<caret> = MockLibrary()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$UseOptInAnnotationFix