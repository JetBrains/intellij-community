// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments
class Foo {
    context(first: Int, second: String)
    operator fun invoke(param1: String, param2: Int) {}
}

fun usage(foo: Foo) {
    foo(<caret>"hello", 10, first = 10, second = "20")
}