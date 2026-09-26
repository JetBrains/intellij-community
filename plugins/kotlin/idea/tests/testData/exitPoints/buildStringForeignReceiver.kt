// WITH_STDLIB
fun f() {
    val string = <info descr="null">~buildString</info> {
        <info descr="null">append</info>("a")
        val other = StringBuilder()
        other.append("b")              // foreign receiver — must NOT be highlighted
        other.appendLine("c")          // foreign receiver — must NOT be highlighted
        <info descr="null">append</info>("d")
        this.<info descr="null">append</info>("e")
        this@buildString.<info descr="null">appendLine</info>("f")
    }
}