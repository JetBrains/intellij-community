// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info>  {  // Caret
        for (i in 1..5) {
            <info descr="null">yield(i)</info>       // Highlighted
        }

        if (true) {
            <info descr="null">yield(100)</info>     // Highlighted
        } else {
            <info descr="null">yieldAll(emptyList())</info>   // Highlighted
        }

        when (1) {
            1 -> <info descr="null">yield(200)</info>         // Highlighted
            else -> <info descr="null">yield(300)</info>      // Highlighted
        }
    }
}