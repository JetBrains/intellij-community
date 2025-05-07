// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C {
    val default: Int = 0
}

context(<caret>C)
fun f(param: Int = default) {
}
