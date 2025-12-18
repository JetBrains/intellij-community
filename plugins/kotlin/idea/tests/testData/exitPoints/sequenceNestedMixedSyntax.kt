// WITH_STDLIB
fun f() {
    val outer = sequence {  // Trailing lambda
        yield(1)

        val inner = <info descr="null">sequence</info>({  // Parenthesized
            <info descr="null">~yield(99)</info>
        })

        yield(2)
    }
}