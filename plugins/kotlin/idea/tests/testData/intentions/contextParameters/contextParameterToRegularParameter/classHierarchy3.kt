// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(<caret>s: String)
    fun foo()
}

open class OpenClass : IFace {
    context(s: String)
    override fun foo() {}
}

class FinalClass : OpenClass(), IFace {
    context(s: String)
    override fun foo() {}
}
