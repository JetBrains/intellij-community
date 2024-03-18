fun foo(f: (String?) -> Int) {}

fun test() {
    foo <info descr="null">{</info>
        if (it == null) <info descr="null">return@~foo 1</info>
        <info descr="null">0</info>
    <info descr="null">}</info>
}