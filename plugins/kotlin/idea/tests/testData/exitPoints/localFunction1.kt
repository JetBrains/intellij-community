fun f(a: Int): Int {
    <info descr="null">fun</info> localFun() {
        <info descr="null">~return</info>
    }

    if (a < 5) {
        return 1
    }
    else {
        return 2
    }
}