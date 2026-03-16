// IGNORE_K1
fun test(list: List<Int>) {
    <caret>if (list.isEmpty()) /* comment line 1 */ listOf()
    else list
}