// API_VERSION: 1.8

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Interfaces in this library are experimental for implementation"
)
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

@OptIn(UnstableApi::class)<caret>
var apiUseSiteProperty: CoreLibraryApi? = null
    get() = null
    set(value) {
        field = value
    }