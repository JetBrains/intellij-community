// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun foo(<caret>s: String)

    private fun usage1(s: String) {
        foo(s)
    }

    private fun String.usage2() {
        foo(this)
    }

    context(s: String)
    private fun usage3() {
        foo(s)
    }
}

open class OpenClass : IFace {
    override fun foo(s: String) {}

    private fun usage1(s: String) {
        foo(s)
    }

    private fun String.usage2() {
        foo(this)
    }

    context(s: String)
    private fun usage3() {
        foo(s)
    }
}

class FinalClass : OpenClass(), IFace {
    override fun foo(s: String) {}

    private fun usage1(s: String) {
        foo(s)
    }

    private fun String.usage2() {
        foo(this)
    }

    context(s: String)
    private fun usage3() {
        foo(s)
    }
}
