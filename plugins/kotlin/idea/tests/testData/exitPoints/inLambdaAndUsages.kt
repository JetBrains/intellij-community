fun foo(f: (String?) -> Int) {}

fun test() {
    foo ~{
        if (it == null) <info descr="null">return@foo 1</info>
        (1+1)
        if (it == "a") <info descr="null">2</info> else <info descr="null">0</info>
    }
}

fun test2() {
    foo {
        if (it == "a") 2 else 0
    }
}