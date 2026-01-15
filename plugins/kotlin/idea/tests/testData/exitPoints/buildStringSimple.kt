// WITH_STDLIB
fun f() {
    val str = <info descr="null">~buildString</info> {
        <info descr="null">append</info>("hello")
        <info descr="null">append</info>(" ")
        <info descr="null">appendLine</info>("world")
    }
}