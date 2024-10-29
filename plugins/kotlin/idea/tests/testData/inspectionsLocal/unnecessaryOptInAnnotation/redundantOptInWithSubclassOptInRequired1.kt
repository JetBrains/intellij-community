// API_VERSION: 2.1
// WITH_STDLIB

@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApiA

@RequiresOptIn
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

class ApiUseSiteParameter(
    <caret>@OptIn(UnstableApiA::class) val foo: CoreLibraryApi
) {
}