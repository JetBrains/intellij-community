// PROBLEM: none
// WITH_STDLIB
// API_VERSION: 2.2

@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiA

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

@OptIn(<caret>UnstableApiA::class, UnstableApiB::class)
val apiUseSiteProperty = object : CoreLibraryApi {}
