// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments
context(text: String, _: Int)
fun foo(string: String) {
    print(string)
}

fun test() {
    foo(string = "hello", text = ""<caret>)
}