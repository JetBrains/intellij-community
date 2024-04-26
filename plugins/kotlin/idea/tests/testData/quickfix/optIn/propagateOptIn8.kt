// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'" "false"
// IGNORE_K2
// ERROR: This declaration needs opt-in. Its usage must be marked with '@UnstableApi' or '@OptIn(UnstableApi::class)'
// ACTION: Add '-opt-in=UnstableApi' to module light_idea_test_case compiler arguments
// ACTION: Introduce import alias
// ACTION: Opt in for 'UnstableApi' in containing file 'propagateOptIn8.kt'
// ACTION: Opt in for 'UnstableApi' on 'foo'
// ACTION: Propagate 'UnstableApi' opt-in requirement to 'foo'
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApi

interface MockApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

fun foo() {
    open class LocalClass : CoreLibrary<caret>Api
}