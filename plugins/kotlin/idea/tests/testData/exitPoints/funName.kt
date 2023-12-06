fun <info descr="null">~test</info>(s: String?): Int {
    if (s != null) {
        return@<info descr="null">test</info> 1
    }
    return 0
}