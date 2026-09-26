// WITH_STDLIB
fun f(x: Int) {
    val s = buildString {~
        append("a")
        if (x == 0) <info descr="null">return@buildString</info>
        append("b")
    }
}