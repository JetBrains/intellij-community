fun some(a: Int, b: Int) {
    val q = <info descr="null">run</info>~ {
        val y = if (a == 1) 0 else 1

        <info descr="null">y</info>
    }
}

fun <T, R> T.run(block: T.() -> R): R = block()