// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Syntax error: Incomplete code.

class A {
    private context(string: <caret><>.String) fun test() {}
}