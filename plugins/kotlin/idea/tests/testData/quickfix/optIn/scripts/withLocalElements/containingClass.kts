// "Opt in for 'Library' on 'a'" "true"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Move to constructor
// ACTION: Opt in for 'Library' in containing file 'containingClass.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'a'
// ACTION: Opt in for 'Library' on containing class 'Bar'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary


@Library
val foo: MockLibrary = MockLibrary();

{
    class Bar() {
        val a = foo<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix