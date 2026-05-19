// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger

context(_: Logger)
fun process(data: String) {}

fun test() {
    val log = Logger()
    context(log) {
        <caret>process(data = "hello")
    }
}
