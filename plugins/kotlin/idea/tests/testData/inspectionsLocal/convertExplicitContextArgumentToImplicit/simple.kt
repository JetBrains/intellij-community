// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class C {
    context(s: String)
    fun foo(i: Int) {}

    fun test() {
        foo(1, <caret>s = "hello")
    }
}
