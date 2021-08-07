// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test(list: List<String>, idx: Int) {
    val s = list[idx]
    if (<caret>idx > list.size) {}
    println()
    if (idx > list.size) {}
}
