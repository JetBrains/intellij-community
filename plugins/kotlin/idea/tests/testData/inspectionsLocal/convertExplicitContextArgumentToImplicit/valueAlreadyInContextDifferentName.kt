// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class C {
    context(s: String)
    fun foo(i: Int) {}

    context(str: String)
    fun test() {
        foo(1, <caret>s = str)
    }
}
