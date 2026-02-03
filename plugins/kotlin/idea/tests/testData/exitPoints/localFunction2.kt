<info descr="null">fun</info> f(a: Int): Int {
    fun localFun() {
        return
    }

    if (a < 5) {
        <info descr="null">return 1</info>
    }
    else {
        <info descr="null">~return 2</info>
    }
}