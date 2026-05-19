// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

class Logger

context(log: Logger)
fun process(data: String) {}

fun test() {
    val log = Logger()
    context(log) {
        // Important comment about logging
        <caret>process(data = "hello")
    }
}