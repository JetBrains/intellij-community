// IGNORE_K1
fun test(list: List<Int>) {
    if<caret> (list.isEmpty()) listOf()
    else {
        /* comment in else
            before expression */
        list
        /* comment in else
            after expression */
    }
}