// "Create secondary constructor" "false"
// ERROR: Type mismatch: inferred type is A but B was expected
// ERROR: Too many arguments for public constructor A() defined in A
// K2_ERROR: Initializer type mismatch: expected 'B', actual 'A'.
// K2_ERROR: Too many arguments for 'constructor(): A'.
// K2_AFTER_ERROR: Initializer type mismatch: expected 'B', actual 'A'.
// K2_AFTER_ERROR: Too many arguments for 'constructor(): A'.

class A

class B

fun test() {
    val b: B = A(<caret>1)
}