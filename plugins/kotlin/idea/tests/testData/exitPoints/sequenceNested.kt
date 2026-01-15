// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~sequence</info> {  // Caret
        <info descr="null">yield</info>(1)            // Highlighted

        val inner = sequence {  // Nested sequence
            yield(99)           // NOT highlighted
            yield(100)          // NOT highlighted
        }

        <info descr="null">yield</info>(2)           // Highlighted
        <info descr="null">yieldAll</info>(inner)     // Highlighted
    }
}