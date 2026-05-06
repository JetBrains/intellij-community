// WITH_STDLIB
fun f() {
    val list = <info descr="null">~buildList</info> {
        <info descr="null">add</info>(1)
        <info descr="null">add</info>(2)
        <info descr="null">addAll</info>(listOf(3, 4))
        var s = 1
        s += 1
        this <info descr="null">+=</info> "d"
    }
}