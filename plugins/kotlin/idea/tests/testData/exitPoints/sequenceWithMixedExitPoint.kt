// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {
        <info descr="null">yield</info>(1)

        buildList {
            add(2)
            <info descr="null">yield</info>(3)  // Still belongs to sequence (not buildList's exit point)
        }

        <info descr="null">yield</info>(4)
    }
}