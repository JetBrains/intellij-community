fun foo(a: List<Int>?) {
    <info descr="null">for</info>~ (i in 1..10) {
        val x = a ?: <info descr="null">break</info>
        for (j in x) {
            //
        }
    }
}
