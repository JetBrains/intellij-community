// "Configure arguments for the feature: multi dollar interpolation" "true"
// K2_ERROR: The feature "multi dollar interpolation" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xmulti-dollar-interpolation', but note that no stability guarantees are provided.

fun test() {
    <caret>$$"$Enable me$"
}
