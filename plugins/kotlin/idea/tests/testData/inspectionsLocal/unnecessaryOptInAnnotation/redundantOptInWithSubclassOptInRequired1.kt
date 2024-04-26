// API_VERSION: 1.8
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Interfaces in this library are experimental for implementation"
)
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

class ApiUseSiteParameter(
    <caret>@OptIn(UnstableApi::class) val foo: CoreLibraryApi
) {
}