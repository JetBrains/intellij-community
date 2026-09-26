// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info> {
        <info descr="null">yield</info>(1)
        val other = mutableListOf<Int>()
        other.add(2)                       // foreign receiver — must NOT be highlighted
        other.addAll(listOf(3))            // foreign receiver — must NOT be highlighted
        <info descr="null">yield</info>(4)
        this.<info descr="null">yield</info>(5)
        this@sequence.<info descr="null">yield</info>(6)
        <info descr="null">yieldAll</info>(listOf(7, 8))
    }
}