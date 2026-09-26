// WITH_STDLIB
fun f() {
    val set = mutableSetOf("a", "b", "c")
    val result = <info descr="null">buildSet</info> {
        <info descr="null">addAll</info>(set)
        this <info descr="null">~-=</info> "b"
    }
}