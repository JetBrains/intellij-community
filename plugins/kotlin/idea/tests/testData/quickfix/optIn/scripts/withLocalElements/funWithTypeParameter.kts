// "Opt in for 'Library' on 'bar'" "true"
// ACTION: Add '-opt-in=FunWithTypeParameter.Library' to module light_idea_test_case compiler arguments
// ACTION: Add full qualifier
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Opt in for 'Library' in containing file 'funWithTypeParameter.kts'
// ACTION: Opt in for 'Library' on 'bar'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    fun bar(test: MockLibrary<caret>){
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix