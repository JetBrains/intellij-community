// COMPILER_ARGUMENTS: -Xcontext-parameters

class Context
class Unrelated

context(<caret>_: Context)
fun foo(context: Unrelated) {
    bar()
}

context(c: Context)
fun bar() {
}
