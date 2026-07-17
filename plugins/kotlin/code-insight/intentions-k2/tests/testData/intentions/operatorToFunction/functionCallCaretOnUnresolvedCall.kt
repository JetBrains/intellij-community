// ERROR: Unresolved reference: unresolved
// IS_APPLICABLE: false
// K2_ERROR: UNRESOLVED_REFERENCE
val unresolvedCallResult = unresolved()
val a = unresolvedCallResult.foo<caret>()