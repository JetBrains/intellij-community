// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun String.foo()
}

open class OpenClass : IFace {
    override fun <caret>String.foo() {}
}

class FinalClass : OpenClass(), IFace {
    override fun String.foo() {}
}