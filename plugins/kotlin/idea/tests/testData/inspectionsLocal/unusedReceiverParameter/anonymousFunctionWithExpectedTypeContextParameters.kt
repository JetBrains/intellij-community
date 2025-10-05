// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters


@DslMarker
annotation class StateKeeperDsl

@StateKeeperDsl
interface StateKeeperBuilder

@StateKeeperDsl
class StateKeeperScope {
    context(_: StateKeeperBuilder)
    fun add() {}
}

fun stateKeeper(block: context(StateKeeperBuilder) StateKeeperScope.() -> Unit) {}

fun test() {
    stateKeeper(context(_: StateKeeperBuilder) fun StateK<caret>eeperScope.() {
    })
}

// IGNORE_K1