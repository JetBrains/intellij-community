// WITH_STDLIB
fun f(): Sequence<Int> {
    return <info descr="null">~sequence</info> {
        <info descr="null">yield</info>(1)

        if(true) {
            return @sequence
        }

        <info descr="null">yield</info>(2)
    }
    // return 0
}