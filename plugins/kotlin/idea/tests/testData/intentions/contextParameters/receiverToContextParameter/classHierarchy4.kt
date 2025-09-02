// COMPILER_ARGUMENTS: -Xcontext-parameters

interface IFace {
    fun <caret>String.foo()

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
        s.foo()
    }
}

open class OpenClass : IFace {
    override fun String.foo() {}

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
        s.foo()
    }
}

class FinalClass : OpenClass(), IFace {
    override fun String.foo() {}

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
        s.foo()
    }
}
