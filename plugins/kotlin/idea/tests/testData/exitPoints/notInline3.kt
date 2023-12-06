<info descr="null">fun</info> foo(x: Int): Int {
    if (x == 1) <info descr="null">return 1</info>
    listOf(1, 2, 3).map {
        if (it == 2) return@map 2
        return@map 3
    }
    <info descr="null">~return 4</info>
}