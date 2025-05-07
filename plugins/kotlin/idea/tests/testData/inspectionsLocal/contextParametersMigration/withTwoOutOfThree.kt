// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C1 { fun fn1() {} }
class C2 { fun Int.fn2() {} }
class C3 { fun Int.fn3() {} }

context(<caret>C1, C2, C3)
fun test() {
    fn1()
    2.fn2()
    3.fn3()
}
