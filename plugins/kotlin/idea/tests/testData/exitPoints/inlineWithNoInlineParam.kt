fun f(a: Int): Int {
    if (a < 5) {
        run1(<info descr="null">fun</info> (): Int {
            <info descr="null">~return 1</info>
        })
    }
    return 2
}

inline public fun <T> run1(noinline f: () -> T): T { }