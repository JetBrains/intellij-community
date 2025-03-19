<info descr="null">fun</info> f(a: Int): Int {
    if (a < 5) {
        run {
            <info descr="null">~return 1</info>
        }
    }
    else {
        <info descr="null">return 2</info>
    }
}

inline public fun <T> run(f: () -> T): T { }