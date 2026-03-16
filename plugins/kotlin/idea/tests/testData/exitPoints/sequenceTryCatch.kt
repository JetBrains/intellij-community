// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {  // Caret
        try {
            <info descr="null">yield</info>(1)      // Highlighted
        } catch (e: Exception) {
            <info descr="null">yield</info>(-1)     // Highlighted
        } finally {
            // yield(0)   // not allowed in finally
        }
    }
}