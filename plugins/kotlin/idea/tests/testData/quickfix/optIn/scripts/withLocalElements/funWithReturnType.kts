// "Opt in for 'Library' on 'bar'" "true"
// ACTION: Add '-opt-in=FunWithReturnType.Library' to module light_idea_test_case compiler arguments
// ACTION: Add full qualifier
// ACTION: Convert to block body
// ACTION: Do not show implicit receiver and parameter hints
// ACTION: Introduce import alias
// ACTION: Opt in for 'Library' in containing file 'funWithReturnType.kts'
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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix