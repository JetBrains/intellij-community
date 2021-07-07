// IGNORE_FIR

val fnType : suspend () -> Unit = {}

val fnFnType: () -> suspend () -> Unit = {  -> {}}

suspend fun inSuspend(fn: suspend () -> Unit) {
    val res: suspend (Int) -> Int = { it + 1 };
    T2().nonSuspend()
    .suspend1(fn)
    .suspend1 {  }
        .suspend1 { res(5) }
    res(5)
    fnType()
    fnFnType().invoke()
}
class T2 {
    suspend inline fun suspend1(block: suspend () -> Unit): T2 {
        block()
        return this
    }
    fun nonSuspend() = this
}
