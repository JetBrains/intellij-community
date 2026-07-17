// "Propagate 'B' opt-in requirement to 'SomeImplementation'" "true"
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR
@file:OptIn(ExperimentalSubclassOptIn::class)

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