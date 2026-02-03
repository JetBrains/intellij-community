// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters

class Executor {
}

fun interface Lamba {
    context(e: Executor)
    fun foo()
}

abstract class BaseTest {
    private val executor = Executor()

    fun notebookTest(
        lambda: Lamba
    ) {
        context(executor) {
            lambda.foo()
        }
    }

    fun runTest() {
        notebookTest(obj<caret>ect : Lamba {
            context(e: Executor)
            override fun foo() {
                bar()
            }
        })
    }

    context(e: Executor)
    fun bar() {}
}

// IGNORE_K1