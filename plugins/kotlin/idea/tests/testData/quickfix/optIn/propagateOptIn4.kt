// "Propagate 'UnstableApi' opt-in requirement to 'SomeImplementation'" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

sealed interface SomeImplementation : CoreLibraryApi<caret>