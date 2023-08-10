fun yy(): Int = 5

<info descr="null">fun</info> f(a: Int): Int {
    <info descr="null">return</info>~ when {
        a < 0 -> {
            val q = 1
            <info descr="null">2 * 2</info>
        }
        a > 0 -> {
            <info descr="null">2 * (2 + 2)</info>
        }
        else -> <info descr="null">yy()</info>
    }
}