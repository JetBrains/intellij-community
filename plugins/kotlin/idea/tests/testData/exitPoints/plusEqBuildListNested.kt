// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~buildList</info> {
        <info descr="null">add</info>(1)

        val inner = buildList {
            add(99)
            this += 100
        }

        <info descr="null">add</info>(2)
        <info descr="null">addAll</info>(inner)
        this <info descr="null">+=</info> 3
    }
}

