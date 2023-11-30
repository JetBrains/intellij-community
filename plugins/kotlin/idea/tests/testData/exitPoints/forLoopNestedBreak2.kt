fun some() {
    <info descr="null">for</info>~ (j in 0..5) {
        if (j == 1) <info descr="null">break</info>
        for (i in 0..5) {
            break
        }
        loop@ for (i in 0..5) {
            break
        }
        if (j == 2) <info descr="null">continue</info>
    }
}
