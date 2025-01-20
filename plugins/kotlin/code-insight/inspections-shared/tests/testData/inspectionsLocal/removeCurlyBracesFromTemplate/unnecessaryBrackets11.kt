// ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// AFTER_ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// K2_AFTER_ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING

fun foo() {
    val x = 4
    val y = $$$$$"$$$$$<caret>{x}"
}