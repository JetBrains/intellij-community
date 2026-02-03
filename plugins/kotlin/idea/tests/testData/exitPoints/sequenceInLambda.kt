// WITH_STDLIB
fun f() {
    listOf(1, 2, 3).map { n ->
        <info descr="null">sequence</info> {                      // Highlighted
            <info descr="null">~yield(n)</info>                   // Highlighted
            <info descr="null">yield(n * 2)</info>                // Highlighted
        }
    }
}