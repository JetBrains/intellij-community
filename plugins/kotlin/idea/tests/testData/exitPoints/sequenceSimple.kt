// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {  // Caret
        <info descr="null">yield(1)</info>           // Highlighted
        <info descr="null">yield(2)</info>           // Highlighted
        <info descr="null">yieldAll(listOf(3, 4))</info>  // Highlighted
    }
}