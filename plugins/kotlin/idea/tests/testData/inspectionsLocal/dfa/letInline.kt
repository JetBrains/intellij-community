// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test(x: String?):Boolean {
    // if x is non-null we return from inner condition.
    // as a result, outer one can only evaluate to false.
    return <caret>x?.let { return x.isEmpty() } ?: false
}