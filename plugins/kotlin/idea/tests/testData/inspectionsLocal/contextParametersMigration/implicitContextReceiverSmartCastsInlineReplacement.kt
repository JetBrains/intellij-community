// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

interface BaseContext
interface ExtendedContext {
    fun baz() {}
}
fun ExtendedContext.bar() {}

context(<caret>BaseContext)
fun usage() {
    if (this@BaseContext is ExtendedContext) {
        bar()
        baz()
    }
}
