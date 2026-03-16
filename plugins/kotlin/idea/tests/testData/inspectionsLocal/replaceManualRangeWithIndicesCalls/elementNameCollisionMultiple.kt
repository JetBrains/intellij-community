// WITH_STDLIB
// FIX: Replace with loop over elements
fun test() {
    val element = "first"
    val element1 = "second"
    val list = mutableListOf<String>("hello", "world")
    for (i in 0 unt<caret>il list.size) {
        list[i].length
    }
}
