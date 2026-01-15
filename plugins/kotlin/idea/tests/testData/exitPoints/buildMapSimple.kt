// WITH_STDLIB
fun f() {
    val map = <info descr="null">~buildMap</info> {
        <info descr="null">put</info>("a", 1)
        <info descr="null">put</info>("b", 2)
        <info descr="null">putAll</info>(mapOf("c" to 3))
    }
}