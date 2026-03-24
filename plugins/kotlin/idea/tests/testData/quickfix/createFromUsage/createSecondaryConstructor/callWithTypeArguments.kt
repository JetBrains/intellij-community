// "Create secondary constructor" "false"
// ERROR: No type arguments expected for constructor A()
// ERROR: Too many arguments for public constructor A() defined in A
// K2_ERROR: No type arguments expected for 'constructor(): A'.
// K2_ERROR: Too many arguments for 'constructor(): A'.
// K2_AFTER_ERROR: No type arguments expected for 'constructor(): A'.
// K2_AFTER_ERROR: Too many arguments for 'constructor(): A'.

class A

fun test() {
    val a = A<Int>(<caret>1)
}