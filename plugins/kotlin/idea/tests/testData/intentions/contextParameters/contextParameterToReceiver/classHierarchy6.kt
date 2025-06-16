// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(<caret>s: String)
    fun foo()
}

open class OpenClass : IFace {
    context(s: String)
    override fun foo() {
        s.length
        withContext()
        s.withReceiver()
    }
}

class FinalClass : OpenClass(), IFace {
    context(s: String)
    override fun foo() {}
}

context(c: String)
fun withContext() {}

fun String.withReceiver() {}
