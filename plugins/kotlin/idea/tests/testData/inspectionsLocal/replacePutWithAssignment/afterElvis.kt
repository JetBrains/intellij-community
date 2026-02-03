// PROBLEM: none
// WITH_STDLIB

fun test(i: Int?, m: MutableMap<String, Int>) {
    i ?: m.<caret>put("", 1)
}