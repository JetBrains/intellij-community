// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun <caret>String.foo()
}

open class OpenClass : IFace {
    override fun String.foo() {
        length
        withContext()
        withReceiver()
    }
}

class FinalClass : OpenClass(), IFace {
    override fun String.foo() {}
}

context(c: String)
fun withContext() {}

fun String.withReceiver() {}
