// AFTER_ERROR: Unresolved reference: a
// K2_AFTER_ERROR: Unresolved reference 'a'.
fun foo() {
    <caret>if (a) {
        println("a" +
           "b")
    }
}
