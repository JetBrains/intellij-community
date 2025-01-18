// IS_APPLICABLE: true
// K2-ERROR: Argument type mismatch: actual type is 'Function0<ERROR CLASS: Unknown return lambda parameter type>', but 'Int' was expected.
// K2-ERROR: No value passed for parameter 'b'.
// K2-ERROR: Unresolved reference 'it'.
// ERROR: Type mismatch: inferred type is () -> ??? but Int was expected
// ERROR: No value passed for parameter 'b'
// ERROR: Unresolved reference: it
// SKIP_ERRORS_AFTER
fun foo() {
    bar(<caret>{ it })
}

fun bar(a: Int, b: (Int) -> Int) {
    b(a)
}
