// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class Ctx {
    fun foo() {}
}

fun Ctx.bar() {}

class MyClass {
    context(<caret>Ctx)
    fun String.test() {
        this + "!"
        foo()
        bar()
    }
}
