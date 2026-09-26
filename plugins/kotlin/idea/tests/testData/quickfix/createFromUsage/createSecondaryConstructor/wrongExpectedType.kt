// "Create secondary constructor" "false"
// ERROR: Type mismatch: inferred type is A but B was expected
// ERROR: Too many arguments for public constructor A() defined in A
// K2_AFTER_ERROR: INITIALIZER_TYPE_MISMATCH
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: INITIALIZER_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS

class A

class B

fun test() {
    val b: B = A(<caret>1)
}