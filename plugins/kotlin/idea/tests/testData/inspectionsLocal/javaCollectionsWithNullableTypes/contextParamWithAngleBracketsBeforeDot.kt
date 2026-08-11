// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: SYNTAX

class A {
    private context(string: <caret><>.String) fun test() {}
}