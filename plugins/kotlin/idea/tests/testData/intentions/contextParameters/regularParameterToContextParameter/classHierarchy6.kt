// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun foo(<caret>s: String)
}

open class OpenClass : IFace {
    override fun foo(s: String) {
        s.length
        with(s) {
            withContext()
        }
        s.withReceiver()
    }
}

class FinalClass : OpenClass(), IFace {
    override fun foo(s: String) {}
}

context(c: String)
fun withContext() {}

fun String.withReceiver() {}
