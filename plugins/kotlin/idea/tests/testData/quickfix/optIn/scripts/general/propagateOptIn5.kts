// "Propagate 'UnstableApi' opt-in requirement to 'SomeImplementation'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

enum class SomeImplementation : CoreLibraryApi<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix