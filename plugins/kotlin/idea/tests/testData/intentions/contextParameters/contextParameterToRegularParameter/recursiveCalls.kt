// COMPILER_ARGUMENTS: -Xcontext-parameters

context(ctx: A) fun <A> implicit():A = ctx

interface TestLogger {
    fun log(message: String)
}

context(<caret>_: TestLogger)
fun logRecursive(n: Int) {
    if (n <= 0) return
    implicit<TestLogger>().log("n = $n")
    logRecursive(n - 1)
}