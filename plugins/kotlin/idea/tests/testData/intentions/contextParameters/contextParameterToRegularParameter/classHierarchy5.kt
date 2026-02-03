// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(<caret>s: String, i: Int)
    fun foo(d: Double)

    context(s: String, i: Int)
    fun foo()
}

open class OpenClass : IFace {
    context(s: String, i: Int)
    override fun foo(d: Double) {}

    context(s: String, i: Int)
    override fun foo() {}
}

class FinalClass : OpenClass(), IFace {
    context(s: String, i: Int)
    override fun foo(d: Double) {}

    context(s: String, i: Int)
    override fun foo() {}
}
