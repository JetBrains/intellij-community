// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~buildString</info> {
        <info descr="null">append</info>("start")

        val inner = buildString {
            append("inner")
        }

        <info descr="null">append</info>(inner)
        <info descr="null">appendLine</info>("end")
    }
}