// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_AFTER_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C1 {
    fun fn() {}
}

context(<caret>C1)
fun f1() {
    f2()
}

context(C1)
fun f2() {
    fn()
}
