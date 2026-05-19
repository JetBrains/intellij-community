// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class C {
    context(s: String)
    fun foo(i: Int) {}

    fun nextInt(): Int = 1
    fun nextString(): String = "hello"

    fun test() {
        foo(nextInt(), <caret>s = nextString())
    }
}
