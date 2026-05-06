// WITH_STDLIB
fun f() {
    val list = <info descr="null">buildList</info> {
        <info descr="null">add</info>("a")
        <info descr="null">addAll</info>(listOf("b", "c"))
        this <info descr="null">~+=</info> "d"
        this <info descr="null">+=</info> "e"
    }
}