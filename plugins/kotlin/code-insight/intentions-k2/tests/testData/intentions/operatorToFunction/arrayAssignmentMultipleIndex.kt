// ERROR: Unresolved reference: array
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun foo(b: Int) {
    var a = array(1, 2, 3, 4, 5)
    a<caret>[2, 3] = b
}
