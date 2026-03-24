// WITH_STDLIB
// FIX: Replace with 'withIndex()'
fun test() {
    val element = "existing"
    val list = listOf("a", "b", "c")
    for (i in 0 unt<caret>il list.size) {
        println("Index $i: ${list[i]}, also $element")
    }
}
