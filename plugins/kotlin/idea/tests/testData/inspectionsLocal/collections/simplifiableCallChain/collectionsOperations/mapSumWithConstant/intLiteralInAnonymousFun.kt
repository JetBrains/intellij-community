// WITH_STDLIB
fun test(list: List<Int>) {
    list.<caret>map(fun(it: Int): Int {
        return 1
    }).sum()
}