// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'" "true"
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApi

interface MockApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

abstract class SomeImplementation : MockApi, CoreLibrary<caret>Api
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix