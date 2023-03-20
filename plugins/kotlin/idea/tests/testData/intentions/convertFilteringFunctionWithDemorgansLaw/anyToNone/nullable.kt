// WITH_STDLIB
fun test(list: List<Int>?): Boolean {
    return list?.<caret>any { it == 1 } == true
}