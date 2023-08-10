fun some(list: List<String>) {
    loop@ <info descr="null">for</info>~ (s in l) {
        if (s == "a") <info descr="null">break@loop</info>
        if (s == "b") <info descr="null">continue</info>

        for (s1 in l) {
            if (s1 == "a") break
            if (s1 == "b") continue
            if (s1 == "c") <info descr="null">break@loop</info>
        }
    }
}
