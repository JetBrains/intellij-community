// "Opt in for 'Library' on 't'" "true"
// ACTION: Add '-opt-in=DoubleExperementalApi2.Library' to module light_idea_test_case compiler arguments
// ACTION: Enable 'Types' inlay hints
// ACTION: Opt in for 'Library' in containing file 'doubleExperementalApi2.kts'
// ACTION: Opt in for 'Library' on 'bar'
// ACTION: Opt in for 'Library' on 't'
// RUNTIME_WITH_SCRIPT_RUNTIME
@RequiresOptIn
annotation class Library()

@Library
class MockLibrary{
    fun bar(test: () -> Unit) = ""
}


@Library
val foo: MockLibrary = MockLibrary();

{
    fun bar() {
        val t = foo.bar<caret> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityUseOptInAnnotationFix