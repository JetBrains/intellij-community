// WITH_STDLIB
// PROBLEM: Replace with 'isNullOrEmpty()' call
// FIX: Replace with 'isNullOrEmpty()' call
fun test(list: List<Int>?) {
    if (<caret>list == null || list.isEmpty()) println(0) else println(list.size)
}
