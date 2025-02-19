// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class Ctx1 { fun fn1() {} }
class Ctx2 { fun fn2() {} }
class Ctx3 { fun fn3() {} }
class Ctx4 { fun fn4() {} }

context(<caret>Ctx1, c2: Ctx2, Ctx3, c4: Ctx4)
fun foo() {
    fn1()
    c2.fn2()
    fn3()
    c4.fn4()
}
