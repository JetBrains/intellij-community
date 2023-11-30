<info descr="null">fun</info> f(a: Int): Int {
    if (a < 5) {
        run {
            return@run 1
        }
    }
    else {
        <info descr="null">~return 2</info>
    }
}

inline public fun <T> run(f: () -> T): T { }