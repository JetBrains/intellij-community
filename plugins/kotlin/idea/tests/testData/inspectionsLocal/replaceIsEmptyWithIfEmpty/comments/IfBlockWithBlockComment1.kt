// IGNORE_K1
fun test(list: List<Int>) {
    <caret>if (list.isEmpty()) listOf()
    /*
    comment line 1
    comment line 2
    */
    else list
}