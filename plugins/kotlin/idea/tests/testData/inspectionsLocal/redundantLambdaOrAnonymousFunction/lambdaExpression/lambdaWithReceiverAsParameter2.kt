// WITH_RUNTIME
class Chain

fun complicate(chain: Chain) {
    val vra = (<caret>{ chain: Chain, fn: Chain.() -> Chain ->
        chain.fn()
        chain.fn()
    })(chain, { Chain().also { println(it) } })
}