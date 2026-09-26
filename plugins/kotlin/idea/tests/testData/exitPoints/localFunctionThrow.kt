fun f(a: Int): Int {
    <info descr="null">fun</info> localFun() {
        if (a > 5) {
            <info descr="null">return</info>
        }
        <info descr="null">~throw Error()</info>
    }

    if (a < 5) {
        return 1
    }
    else {
        throw Exception()
    }
}