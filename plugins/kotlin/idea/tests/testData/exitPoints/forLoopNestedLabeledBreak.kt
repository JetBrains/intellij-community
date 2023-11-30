fun some(list: List<String>) {
    a@ <info descr="null">for</info> (j in 0..5) {
        for (i in 0..5) {
            <info descr="null">break@a</info>
        }
        if (j == 0) <info descr="null">continue</info>
        if (j == 2) <info descr="null">break</info>
        b@ for (i in 0..5) {
            break@b
            <info descr="null">brea~k@a</info>
        }
    }
}
