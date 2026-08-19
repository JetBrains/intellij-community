// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    val String.foo: String
        get() = "ha"
}

open class OpenClass : IFace {
    override val String.foo: String
        get() = "hi"
}

class FinalClass : OpenClass(), IFace {
    override val <caret>String.foo: String
        get() = "hu"
}
