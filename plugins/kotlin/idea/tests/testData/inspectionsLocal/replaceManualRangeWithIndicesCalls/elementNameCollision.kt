// WITH_STDLIB
// FIX: Replace with loop over elements
fun test() {
    val element = mutableListOf<String>("hello", "world")
    for (i in 0 unt<caret>il element.size) {
        element[i].length
        element.size
    }
}
