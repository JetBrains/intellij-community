// "Propagate 'UnstableApi' opt-in requirement to 'SomeImplementation'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

class SomeImplementation : CoreLibraryApi<caret>