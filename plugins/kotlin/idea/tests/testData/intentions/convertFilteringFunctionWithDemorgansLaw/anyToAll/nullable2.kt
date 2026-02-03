// WITH_STDLIB
fun test(list: List<Int>?): Boolean {
    return list?.<caret>all { it == 1 }?.not() == true
}