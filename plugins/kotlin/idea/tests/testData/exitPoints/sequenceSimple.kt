// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {  // Caret
        <info descr="null">yield</info>(1)           // Highlighted
        <info descr="null">yield</info>(2)           // Highlighted
        <info descr="null">yieldAll</info>(listOf(3, 4))  // Highlighted
    }
}