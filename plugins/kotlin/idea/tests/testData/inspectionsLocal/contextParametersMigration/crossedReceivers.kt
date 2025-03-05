// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C1 {
    fun C2.f1() {}
}

class C2 {
    fun C1.f2() {}
}

context(<caret>C1, C2)
fun f() {
    f1()
    f2()
}
