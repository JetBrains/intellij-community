// AFTER_ERROR: Unresolved reference: a
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    <caret>if (a) {
        println("a" +
           "b")
    }
}
