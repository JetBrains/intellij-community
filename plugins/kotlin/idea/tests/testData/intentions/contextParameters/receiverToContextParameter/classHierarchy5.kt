// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(i: Int)
    fun <caret>String.foo(d: Double)

    context(i: Int)
    fun String.foo()
}

open class OpenClass : IFace {
    context(i: Int)
    override fun String.foo(d: Double) {}

    context(i: Int)
    override fun String.foo() {}
}

class FinalClass : OpenClass(), IFace {
    context(i: Int)
    override fun String.foo(d: Double) {}

    context(i: Int)
    override fun String.foo() {}
}
