// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C1 { fun fn1() {} }
class C2 { fun fn2() {} }
class C3 { fun fn3() {} }
class C4 { fun fn4() {} }
class C5 { fun fn5() {} }
class C6 { fun fn6() {} }
class C7 { fun fn7() {} }
class C8 { fun fn8() {} }
class C9 { fun fn9() {} }

context(C1, C2, <caret>C3, C4, C5, C6, C7, C8, C9)
fun f() {
    fn1()
    fn2()
    fn3()
    fn4()
    fn5()
    fn6()
    fn7()
    fn8()
    fn9()
}
