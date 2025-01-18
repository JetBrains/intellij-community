// K2-ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// K2-AFTER-ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// IGNORE_K1

fun foo() {
    val x = 4
    val y = $$$$$"$$$$$<caret>{x}"
}