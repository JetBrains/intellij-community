// WITH_STDLIB
fun f() {
    val outer = <info descr="null">~sequence</info> {  // Caret
        <info descr="null">yield(1)</info>            // Highlighted

        val inner = sequence {  // Nested sequence
            yield(99)           // NOT highlighted
            yield(100)          // NOT highlighted
        }

        <info descr="null">yield(2)</info>            // Highlighted
        <info descr="null">yieldAll(inner)</info>     // Highlighted
    }
}