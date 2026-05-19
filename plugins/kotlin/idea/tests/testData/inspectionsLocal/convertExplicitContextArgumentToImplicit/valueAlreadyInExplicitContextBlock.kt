// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class C {
    context(s: String)
    fun foo(i: Int) {}

    fun test(str: String) {
        context(str) {
            foo(1, <caret>s = str)
        }
    }
}