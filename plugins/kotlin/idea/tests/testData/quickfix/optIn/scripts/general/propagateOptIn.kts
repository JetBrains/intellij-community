// "Propagate 'B' opt-in requirement to 'SomeImplementation'" "true"
// ACTION: Implement interface
// ACTION: Introduce import alias
// ACTION: Opt in for 'B' in containing file 'propagateOptIn.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'SomeImplementation'
// ACTION: Propagate 'B' opt-in requirement to 'SomeImplementation'
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@SubclassOptInRequired(A::class)
interface LibraryA

@SubclassOptInRequired(B::class)
interface LibraryB

@SubclassOptInRequired(A::class)
interface SomeImplementation : LibraryA, Libra<caret>ryB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix