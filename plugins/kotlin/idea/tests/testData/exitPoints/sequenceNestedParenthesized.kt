// WITH_STDLIB
fun f() {
    val outer = <info descr="null">sequence</info>({  // Parenthesized outer
        <info descr="null">yield</info>(1)

        val inner = sequence({  // Parenthesized inner
            yield(99)
        })

        <info descr="null">~yield</info>(2)
    })
}