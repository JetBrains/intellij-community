// PROBLEM: none
// WITH_STDLIB
fun test(list: List<Int>, i: Int) {
    list.map<caret> {
        when (i) {
            1 -> {
                if (true) 1 else 0
            }
            2 -> {
                2
            }
            else -> {
                3
            }
        }
    }.sum()
}