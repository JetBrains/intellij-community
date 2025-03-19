// "Propagate 'UnstableApi' opt-in requirement to 'SomeImplementation'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

fun interface SomeImplementation : CoreLibraryApi<caret> {
    fun method()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix