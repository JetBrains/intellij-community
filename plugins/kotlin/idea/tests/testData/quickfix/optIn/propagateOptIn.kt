// "Propagate 'B' opt-in requirement to 'SomeImplementation'" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

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