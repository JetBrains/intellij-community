// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C {
    fun fn() {}
}

context(<caret>C)
fun f() {
    fn()
}
