// K2-ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// PROBLEM: none
fun foo() {
    val x = "x"
    val y = $$"$$<caret>{x.length}"
}