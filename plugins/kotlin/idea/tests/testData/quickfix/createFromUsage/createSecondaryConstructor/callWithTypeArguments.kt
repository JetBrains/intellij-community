// "Create secondary constructor" "false"
// ERROR: No type arguments expected for constructor A()
// ERROR: Too many arguments for public constructor A() defined in A
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_AFTER_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS
// K2_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS

class A

fun test() {
    val a = A<Int>(<caret>1)
}