fun f(ac: () -> Unit) {}
fun m() {
    <info descr="null">~f</info> {
        var i = 0
        i
    }
}