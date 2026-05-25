// WITH_STDLIB
fun f() {
    val map = <info descr="null">buildMap</info> {
        <info descr="null">put</info>("a", 1)
        this <info descr="null">+~=</info> ("b" to 2)
    }
}