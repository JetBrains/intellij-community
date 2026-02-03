// IGNORE_K1
fun test(list: List<Int>) {
    <caret>if (list.isEmpty()) listOf()
    else {
        /* comment in else
            before expression */
        list
        /* comment in else
            after expression */
    }
}