fun f(a: Int): Int {
    if (a < 5) {
        <info descr="null">run</info> {
            <info descr="null">~return@run 1</info>
        }
    }
    else {
        return 2
    }
}

inline public fun <T> run(f: () -> T): T { }