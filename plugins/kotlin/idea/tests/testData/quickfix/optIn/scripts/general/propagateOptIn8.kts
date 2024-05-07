// "Propagate 'SubclassOptInRequired(UnstableApi::class)' opt-in requirement to 'LocalClass'" "false"
// ERROR: This declaration needs opt-in. Its usage must be marked with '@PropagateOptIn8.UnstableApi' or '@OptIn(PropagateOptIn8.UnstableApi::class)'
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ACTION: Opt in for 'UnstableApi' in containing file 'propagateOptIn8.kts'
// ACTION: Opt in for 'UnstableApi' in module 'light_idea_test_case'
// ACTION: Opt in for 'UnstableApi' on 'LocalClass'
// ACTION: Opt in for 'UnstableApi' on 'foo'
// ACTION: Propagate 'UnstableApi' opt-in requirement to 'foo'
// RUNTIME_WITH_SCRIPT_RUNTIME
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApi

interface MockApi

@SubclassOptInRequired(UnstableApi::class)
interface CoreLibraryApi

fun foo() {
    open class LocalClass : CoreLibrary<caret>Api
}
