// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NOT_A_MULTIPLATFORM_COMPILATION
expect class Foo {
    context(<caret>a: String)
    val v: String
}