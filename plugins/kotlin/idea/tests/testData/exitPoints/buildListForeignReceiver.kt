// WITH_STDLIB
fun f() {
    val list = <info descr="null">~buildList</info> {
        <info descr="null">add</info>(1)
        val other = mutableListOf<Int>()
        other.add(2)              // foreign receiver — must NOT be highlighted
        other.addAll(listOf(3))   // foreign receiver — must NOT be highlighted
        <info descr="null">add</info>(4)
        this.<info descr="null">add</info>(2)
        this@buildList.<info descr="null">add</info>(3)
        <info descr="null">addAll</info>(listOf(4, 5))
    }
}