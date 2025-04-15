// "Configure arguments for the feature: context parameters" "true"
// K2_ERROR: The feature "context parameters" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-parameters', but note that no stability guarantees are provided.

context(_: Any)<caret>
fun test() {
}
