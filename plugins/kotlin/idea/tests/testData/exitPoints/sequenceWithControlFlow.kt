// WITH_STDLIB
fun f() {
    val seq = <info descr="null">~sequence</info>  {  // Caret
        for (i in 1..5) {
            <info descr="null">yield</info>(i)       // Highlighted
        }

        if (true) {
            <info descr="null">yield</info>(100)     // Highlighted
        } else {
            <info descr="null">yieldAll</info>(emptyList())   // Highlighted
        }

        when (1) {
            1 -> <info descr="null">yield</info>(200)         // Highlighted
            else -> <info descr="null">yield</info>(300)      // Highlighted
        }
    }
}