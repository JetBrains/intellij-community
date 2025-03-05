// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: SubclassOptInRequired

@file:OptIn(UnstableApiA::class, UnstableApiB::class, ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApiA

@RequiresOptIn
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

interface SomeImplementation : CoreLibraryApi

interface SomeChild : SomeImplementation<caret>

