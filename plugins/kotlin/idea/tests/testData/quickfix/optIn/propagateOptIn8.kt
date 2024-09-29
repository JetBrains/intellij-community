// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'" "false"
// IGNORE_K2
// ERROR: This class or interface requires opt-in to be implemented. Its usage must be marked with '@UnstableApi', '@OptIn(UnstableApi::class)' or '@SubclassOptInRequired(UnstableApi::class)'
// ACTION: Introduce import alias
// ACTION: Opt in for 'UnstableApi' in containing file 'propagateOptIn8.kt'
// ACTION: Opt in for 'UnstableApi' in module 'light_idea_test_case'
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