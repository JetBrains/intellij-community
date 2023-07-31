fun foo(f: (String?) -> Int) {}

fun test() {
    <info descr="null">foo</info> {
        if (it == null) <info descr="null">return@~foo 1</info>
        (1+1)
        if (it == "a") <info descr="null">2</info> else <info descr="null">0</info>
    }
}