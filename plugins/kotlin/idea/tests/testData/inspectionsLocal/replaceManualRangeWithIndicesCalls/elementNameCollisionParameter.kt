// WITH_STDLIB
// FIX: Replace with loop over elements
fun test(element: String, list: List<String>) {
    for (i in 0 unt<caret>il list.size) {
        list[i].length
        element.length
    }
}
