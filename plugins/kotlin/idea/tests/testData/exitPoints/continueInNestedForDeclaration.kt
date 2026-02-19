fun some() {
    val lists = listOf(null, listOf(1))
    <info descr="null">for</info> (list in lists) {
        for (element: Int? in list ?: <info descr="null">continue</info>~) {
            element ?: continue
            foo(element)
        }
    }
}

fun foo(i: Int){}