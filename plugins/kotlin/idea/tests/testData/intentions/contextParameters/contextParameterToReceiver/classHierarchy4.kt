// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    context(<caret>s: String)
    fun foo()

    private fun usage1(s: String) {
        with(s) {
            foo()
        }
    }

    private fun String.usage2() {
        foo()
    }

    context(s: String)
    private fun usage3() {
        foo()
    }
}

open class OpenClass : IFace {
    context(s: String)
    override fun foo() {}

    private fun usage1(s: String) {
        with(s) {
            foo()
        }
    }

    private fun String.usage2() {
        foo()
    }

    context(s: String)
    private fun usage3() {
        foo()
    }
}

class FinalClass : OpenClass(), IFace {
    context(s: String)
    override fun foo() {}

    private fun usage1(s: String) {
        with(s) {
            foo()
        }
    }

    private fun String.usage2() {
        foo()
    }

    context(s: String)
    private fun usage3() {
        foo()
    }
}
