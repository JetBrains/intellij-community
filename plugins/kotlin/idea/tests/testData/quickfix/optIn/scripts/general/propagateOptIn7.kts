// "Propagate 'UnstableApi' opt-in requirement to 'SomeImplementation'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1
// K2_ERROR: This class or interface requires opt-in to be implemented. Its usage must be marked with '@UnstableApi' or '@OptIn(UnstableApi::class)'

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

fun interface SomeImplementation : CoreLibraryApi<caret> {
    fun method()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix