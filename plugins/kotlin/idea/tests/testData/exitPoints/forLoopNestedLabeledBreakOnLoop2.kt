fun some(list: List<String>) {
    loop@ <info descr="null">for</info>~ (s in l) {
        if (s == "a") <info descr="null">break@loop</info>
        if (s == "b") <info descr="null">continue</info>

        loop@ for (s1 in l) {
            if (s1 == "a") break
            if (s1 == "b") continue
            if (s1 == "c") break@loop
        }

        loop2@ for (s1 in l) {
            if (s1 == "a") break
            if (s1 == "b") continue
            if (s1 == "c") <info descr="null">break@loop</info>
            if (s1 == "d") break
        }

        loop1@ for (s1 in l) {
            if (s1 == "a") break
            if (s1 == "b") continue
            loop2@ for (s1 in l) {
                if (s1 == "a") break
                if (s1 == "b") continue
                if (s1 == "c") <info descr="null">break@loop</info>
            }
            if (s1 == "c") break
        }

        if (s == "c") <info descr="null">continue</info>
        if (s == "d") <info descr="null">break</info>
        if (s == "e") <info descr="null">break@loop</info>
    }
}
