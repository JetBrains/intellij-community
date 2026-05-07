// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>?): String? {
    return <caret>if (list!!.isNotEmpty()) {
        list!![0]
    } else {
        null
    }
}
