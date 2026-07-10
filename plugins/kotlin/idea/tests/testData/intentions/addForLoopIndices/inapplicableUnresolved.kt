// IS_APPLICABLE: false
// ERROR: Unresolved reference: b
// K2_ERROR: ITERATOR_AMBIGUITY
// K2_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    for (a <caret>in b) {

    }
}