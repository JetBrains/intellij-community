// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:-ContextParameters
// K2_ERROR: The feature "context parameters" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-parameters', but note that no stability guarantees are provided.

context(<caret>c1: Int)
fun foo() {}
