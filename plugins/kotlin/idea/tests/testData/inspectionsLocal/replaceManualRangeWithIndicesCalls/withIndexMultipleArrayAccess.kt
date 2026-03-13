// WITH_STDLIB
// FIX: Replace with 'withIndex()'
fun test() {
    val list = listOf("a", "b", "c")
    for (i in 0..<caret><list.size) {
        println("Processing index $i")
        val item = list[i]
        println("Item: $item, also ${list[i].length}")
    }
}
