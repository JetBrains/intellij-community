// WITH_STDLIB
class Chain

fun complicate(chain: Chain) {
    val vra = ((<caret>{ chain: Chain, fn: Chain.() -> Chain ->
        chain.fn()
    }))(chain){ this.also { println(it) } }
}