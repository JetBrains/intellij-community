// WITH_STDLIB
fun f() {
    val outer = <info descr="null">sequence</info>({  // Parenthesized outer
        <info descr="null">yield(1)</info>

        val inner = sequence({  // Parenthesized inner
            yield(99)
        })

        <info descr="null">~yield(2)</info>
    })
}