// PROBLEM: none
// WITH_STDLIB
// ERROR: This annotation is not repeatable
// ERROR: This annotation is not repeatable
// ERROR: This annotation is not repeatable
// ERROR: This annotation is not repeatable
// ERROR: This annotation is not repeatable
// ERROR: This annotation is not repeatable
// ERROR: Unresolved reference: SubclassOptInRequired

@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiA

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
interface SomeImplementationInterface : CoreLibraryApi

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
object SomeImplementationObject : CoreLibraryApi

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
class SomeImplementationClass : CoreLibraryApi

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
enum class SomeImplementationEnum : CoreLibraryApi

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
val apiUseSiteProperty = object : CoreLibraryApi {}

@OptIn(UnstableApiA::class)
@OptIn(UnstableApiB::class)
fun test() {
    val apiUseSiteProperty = object : CoreLibraryApi {}
}

class SomeChild : SomeImplementationInterface<caret>
