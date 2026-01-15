// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~buildMap</info> {
        <info descr="null">put</info>("a", 1)

        val inner = buildMap {
            put("x", 99)
        }

        <info descr="null">put</info>("b", 2)
        <info descr="null">putAll</info>(inner)
    }
}