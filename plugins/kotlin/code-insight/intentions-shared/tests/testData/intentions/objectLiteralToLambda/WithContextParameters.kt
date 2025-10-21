// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters

class Executor {
    fun execute(s: String) {}
}
class Completion {
    fun invokeCompletion() {}
}
fun interface Lamba {
    context(e: Executor, c: Completion)
    fun invoke()
}

abstract class BaseTest {
    private val executor = Executor()
    private val completion = Completion()

    fun notebookTest(
        lambda: Lamba
    ) {
        context(executor, completion) {
            lambda.invoke()
        }
    }

    fun runTest() {
        notebookTest(obj<caret>ect : Lamba {
            context(e: Executor, c: Completion)
            override fun invoke() {
                e.execute("1 + 1")
                c.invokeCompletion()
            }
        })
    }
}

// IGNORE_K1