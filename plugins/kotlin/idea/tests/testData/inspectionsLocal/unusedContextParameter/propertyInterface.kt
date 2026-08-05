// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
interface Foo {
    context(<caret>a: String)
    val v2: String get() = "x"
}