fun foo(a: List<Int>?) {
    <info descr="null">for</info>~ (i in 1..10) {
        run {
            for (j in a ?: <info descr="null">continue</info>) {
                //
            }
        }
    }
}
