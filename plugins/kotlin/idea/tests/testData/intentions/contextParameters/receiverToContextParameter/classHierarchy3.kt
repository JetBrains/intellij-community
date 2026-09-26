// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun <caret>String.foo()
}

open class OpenClass : IFace {
    override fun String.foo() {}
}

class FinalClass : OpenClass(), IFace {
    override fun String.foo() {}
}
