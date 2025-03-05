// ERROR: Unresolved reference: unresolved
// K2_ERROR: Unresolved reference 'unresolved'.
// IS_APPLICABLE: false
val unresolvedCallResult = unresolved()
val a = unresolvedCallResult.foo<caret>()