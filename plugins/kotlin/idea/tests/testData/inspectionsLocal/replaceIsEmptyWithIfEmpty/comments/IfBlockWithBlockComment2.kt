// IGNORE_K1
fun test(list: List<Int>) {
    if (list.<caret>isEmpty()) /* comment line 1 */ listOf()
    else list
}