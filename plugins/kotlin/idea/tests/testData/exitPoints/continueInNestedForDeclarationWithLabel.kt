fun some() {
    val lists = listOf(null, listOf(1))
    outer@ <info descr="null">for</info> (list in lists) {
        for (element: Int? in list ?: <info descr="null">continue</info>~) {
            element ?: <info descr="null">continue@outer</info>
            element ?: continue
            foo(element)
        }
    }
}

fun foo(i: Int){}