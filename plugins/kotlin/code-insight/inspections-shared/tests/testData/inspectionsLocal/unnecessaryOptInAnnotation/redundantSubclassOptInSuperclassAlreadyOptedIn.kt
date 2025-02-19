// WITH_STDLIB
// LANGUAGE_VERSION: 2.2

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiA

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

@OptIn(UnstableApiA::class, UnstableApiB::class)
abstract class ImplementationClass() : CoreLibraryApi

@OptIn(<caret>UnstableApiA::class, UnstableApiB::class)
object ImplementationObject : ImplementationClass()
