// PROBLEM: none
// WITH_STDLIB
// API_VERSION: 2.2

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiA

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

@OptIn(<caret>UnstableApiA::class, UnstableApiB::class)
fun test() {
    val apiUseSiteProperty = object : CoreLibraryApi {}
}
