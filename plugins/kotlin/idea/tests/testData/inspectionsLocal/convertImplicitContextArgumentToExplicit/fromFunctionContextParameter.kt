// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger

context(log: Logger)
fun process(data: String) {}

context(logger: Logger)
fun test() {
    <caret>process(data = "hello")
}