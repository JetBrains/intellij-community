// WITH_STDLIB
// FIX: Replace '!=' with 'contentEquals'
fun foo() {
    val a = arrayOf("a", "b", "c")
    val b = arrayOf("a", "b", "c")
    if (a <caret>!= b) {
    }
}
