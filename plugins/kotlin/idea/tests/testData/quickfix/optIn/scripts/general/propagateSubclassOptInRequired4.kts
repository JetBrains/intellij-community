// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'" "true"
// ACTION: Implement abstract class
// ACTION: Introduce import alias
// ACTION: Opt in for 'UnstableApi' in containing file 'propagateSubclassOptInRequired4.kts'
// ACTION: Opt in for 'UnstableApi' in module 'light_idea_test_case'
// ACTION: Opt in for 'UnstableApi' on 'SomeImplementation'
// ACTION: Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

abstract class SomeImplementation : CoreLibraryApi<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix