// PROBLEM: none
// API_VERSION: 1.8
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn(message = "Interfaces in this library are experimental for implementation")
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

@OptIn(UnstableApi::class)
interface SomeImplementationInterface : CoreLibraryApi

@OptIn(UnstableApi::class)
object SomeImplementationObject : CoreLibraryApi

@OptIn(UnstableApi::class)
class SomeImplementationClass : CoreLibraryApi

@OptIn(UnstableApi::class)
enum class SomeImplementationEnum : CoreLibraryApi

@OptIn(UnstableApi::class)
val apiUseSiteProperty = object : CoreLibraryApi {}

@OptIn(UnstableApi::class)
fun test() {
    val apiUseSiteProperty = object : CoreLibraryApi {}
}

class SomeChild : SomeImplementationInterface<caret>
