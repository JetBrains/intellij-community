// "Propagate 'B' opt-in requirement to 'SomeImplementation'" "true"
// ACTION: Implement interface
// ACTION: Introduce import alias
// ACTION: Opt in for 'B' in containing file 'propagateOptIn.kts'
// ACTION: Opt in for 'B' in module 'light_idea_test_case'
// ACTION: Opt in for 'B' on 'SomeImplementation'
// ACTION: Propagate 'B' opt-in requirement to 'SomeImplementation'
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1
// K2_ERROR: This class or interface requires opt-in to be implemented. Its usage must be marked with '@B', '@OptIn(B::class)' or '@SubclassOptInRequired(B::class)'

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