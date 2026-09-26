// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun foo(<caret>s: String)
}

open class OpenClass : IFace {
    override fun foo(s: String) {}
}

class FinalClass : OpenClass(), IFace {
    override fun foo(s: String) {}
}
