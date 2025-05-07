// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class Ctx {
    fun Int.contextFun1(param: MyClass) {}
    fun Int.contextFun2(param: String) {}
    fun Int.contextFun3(param: Int) {}
}

class MyClass {
    context(<caret>Ctx)
    fun foo() {
        this.toString() + "foo"
        42.contextFun1(this@MyClass)
        42.contextFun2(this.toString())
        with(15) {
            42.contextFun3(this)
            42.contextFun3(this@with)
        }
    }
}
