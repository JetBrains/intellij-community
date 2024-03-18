// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'SomeImplementation'" "false"
// IGNORE_K1
// ERROR: This declaration needs opt-in. Its usage must be marked with '@UnstableApi' or '@OptIn(UnstableApi::class)'
// ACTION: Opt in for 'UnstableApi' in containing file 'propagateOptIn8Fir.kt'
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
