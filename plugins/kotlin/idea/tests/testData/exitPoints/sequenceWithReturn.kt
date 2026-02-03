// WITH_STDLIB
fun f(): Sequence<Int> {
    return <info descr="null">~sequence</info> {
        <info descr="null">yield(1)</info>

        if(true) {
            return @sequence
        }

        <info descr="null">yield(2)</info>
    }
    // return 0
}