// "Opt in for 'Library' on containing object" "true"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Introduce local variable
// ACTION: Opt in for 'Library' in containing file 'containingObject.kts'
// ACTION: Opt in for 'Library' in module 'light_idea_test_case'
// ACTION: Opt in for 'Library' on 'a'
// ACTION: Opt in for 'Library' on containing object
// RUNTIME_WITH_SCRIPT_RUNTIME
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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix