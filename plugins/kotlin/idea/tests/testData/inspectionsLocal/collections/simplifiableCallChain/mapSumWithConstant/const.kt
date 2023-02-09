// WITH_STDLIB
const val i = 1
fun test(list: List<Int>) {
    list.map<caret> { i }.sum()
}