// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger

context(c: Logger)
fun process(data: String) {}

context(log: Logger)
fun outer() {
    val log = "shadow"
    <caret>process(data = "hello")
}