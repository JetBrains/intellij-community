fun f(a: Int): Int {
    if (a < 5) {
        run <info descr="null">{</info>
            <info descr="null">~return@run 1</info>
        <info descr="null">}</info>
    }
    else {
        return 2
    }
}

inline public fun <T> run(f: () -> T): T { }