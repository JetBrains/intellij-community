fun foo(f: (String?) -> Int) {}

fun test() {
    <info descr="null">foo</info> {
        if (it == null) <info descr="null">return@~foo 1</info>
        <info descr="null">0</info>
    }
}