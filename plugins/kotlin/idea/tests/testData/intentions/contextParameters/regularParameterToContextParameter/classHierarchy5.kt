// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(i: Int)
    fun foo(<caret>s: String, d: Double)

    context(i: Int)
    fun foo(s: String)
}

open class OpenClass : IFace {
    context(i: Int)
    override fun foo(s: String, d: Double) {}

    context(i: Int)
    override fun foo(s: String) {}
}

class FinalClass : OpenClass(), IFace {
    context(i: Int)
    override fun foo(s: String, d: Double) {}

    context(i: Int)
    override fun foo(s: String) {}
}
