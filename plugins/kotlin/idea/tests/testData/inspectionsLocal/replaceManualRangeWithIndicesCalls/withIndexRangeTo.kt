// WITH_STDLIB
// FIX: Replace with 'withIndex()'
fun test() {
    val arr = arrayOf(1, 2, 3)
    for (idx in 0.<caret>.arr.size - 1) {
        println("arr[$idx] = ${arr[idx]}")
    }
}
