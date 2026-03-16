// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~buildSet</info> {
        <info descr="null">add</info>(1)

        val inner = buildSet {
            add(99)
        }

        <info descr="null">add</info>(2)
        <info descr="null">addAll</info>(inner)
    }
}