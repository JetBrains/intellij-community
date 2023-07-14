// "Propagate 'SubclassOptInRequired(A::class)' opt-in requirement to 'SomeImplementation'" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// ERROR: This declaration needs opt-in. Its usage must be marked with '@B' or '@OptIn(B::class)'
// WITH_STDLIB

@RequiresOptIn
annotation class A

@RequiresOptIn
annotation class B

@SubclassOptInRequired(A::class)
interface LibraryA

@SubclassOptInRequired(B::class)
interface LibraryB

interface SomeImplementation : LibraryA<caret>, LibraryB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixesFactory$PropagateOptInAnnotationFix