// FIX: Replace negated '<' operation with '>=' (may change semantics with floating-point types)
fun test() {
    val x = Double.NaN
    val y = Double.NaN
    <caret>!(x < y)
}