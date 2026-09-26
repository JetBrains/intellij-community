// API_VERSION: 2.1
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Interfaces in this library are experimental for implementation"
)
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

@OptIn(UnstableApi::class)<caret>
fun apiUseSiteFunction(foo: CoreLibraryApi) {
}
