// WITH_STDLIB
fun test(list: List<Int>?): Boolean {
    return list?.<caret>none { it == 1 }?.not() == true
}