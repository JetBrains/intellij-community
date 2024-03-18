// PROBLEM: none
// API_VERSION: 1.8

@file:OptIn(UnstableApi::class, ExperimentalSubclassOptIn::class)

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

interface SomeImplementation : CoreLibraryApi

interface SomeChild : SomeImplementation<caret>

