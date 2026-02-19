// ERROR: The feature "context receivers" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-receivers', but note that no stability guarantees are provided.
// K2_ERROR: The feature "context parameters" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-parameters', but note that no stability guarantees are provided.
// K2_AFTER_ERROR: The feature "context parameters" is experimental and should be enabled explicitly. This can be done by supplying the compiler argument '-Xcontext-parameters', but note that no stability guarantees are provided.
// IGNORE_K1
// IGNORE_K2
// KTIJ-32836
class A

context(A)
<caret>fun foo() {}
