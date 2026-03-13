// WITH_STDLIB
fun f() {
    listOf(1, 2, 3).map { n ->
        <info descr="null">sequence</info> {                      // Highlighted
            <info descr="null">~yield</info>(n)                   // Highlighted
            <info descr="null">yield</info>(n * 2)                // Highlighted
        }
    }
}