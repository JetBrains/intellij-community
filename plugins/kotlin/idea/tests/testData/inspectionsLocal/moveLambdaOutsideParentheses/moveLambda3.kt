// IS_APPLICABLE: true
// ERROR: Type mismatch: inferred type is () -> Unit but Int was expected
// ERROR: No value passed for parameter 'b'
// ERROR: Unresolved reference: it
// AFTER_ERROR: No value passed for parameter 'a'
// K2_AFTER_ERROR: No value passed for parameter 'a'.
// K2_ERROR: Argument type mismatch: actual type is '() -> ??? (Unknown lambda return type)', but 'Int' was expected.
// K2_ERROR: No value passed for parameter 'b'.
// K2_ERROR: Unresolved reference 'it'.
fun foo() {
    bar(<caret>{ it })
}

fun bar(a: Int, b: (Int) -> Int) {
    b(a)
}
