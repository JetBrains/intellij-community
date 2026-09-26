// WITH_STDLIB
fun f() {
    val nested0 = sequence {        // Level 1
        yield(1)

        val nested1 = <info descr="null">sequence</info>  {  // Level 2
            <info descr="null">~yield</info>(10)

            val nested2 = sequence {  // Level 3
                yield(100)
                yield(200)
            }

            <info descr="null">yieldAll</info>(nested2)
            <info descr="null">yield</info>(20)
        }

        yieldAll(nested1)
        yield(2)
    }
}