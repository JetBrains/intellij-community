// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// IGNORE_K2

interface BaseContext
interface ExtendedContext {
    fun Int.foo() {}
    fun baz() {}
    fun BaseContext.boo() {}
}
fun ExtendedContext.bar() {}

context(<caret>BaseContext)
fun usage() {
    if (this@BaseContext is ExtendedContext) {
        42.foo()
        bar()
        baz()
        boo()
    }
}
