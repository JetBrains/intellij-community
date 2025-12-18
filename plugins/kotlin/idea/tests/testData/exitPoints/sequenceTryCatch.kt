// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {  // Caret
        try {
            <info descr="null">yield(1)</info>      // Highlighted
        } catch (e: Exception) {
            <info descr="null">yield(-1)</info>     // Highlighted
        } finally {
            // yield(0)   // not allowed in finally
        }
    }
}