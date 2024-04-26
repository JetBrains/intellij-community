fun foo(x: Int): Int {
    if (x == 1) return 1
    listOf(1, 2, 3).map <info descr="null">{</info>
        if (it == 2) <info descr="null">return@map 2</info>
        <info descr="null">return@~map 3</info>
    <info descr="null">}</info>
    return 4
}