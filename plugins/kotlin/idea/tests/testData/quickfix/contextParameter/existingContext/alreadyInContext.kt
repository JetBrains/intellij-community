// "Add 'extraLogger' as 'MyLogger' to existing context" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K1
// DISABLE_K2_ERRORS
interface MyLogger { fun log(msg: String) }
class ConsoleLogger : MyLogger { override fun log(msg: String) {} }

context(l: MyLogger) fun emit() { l.log("x") }

fun repro(extraLogger: MyLogger) {
    context(ConsoleLogger()) {
        <caret>emit()
    }
}