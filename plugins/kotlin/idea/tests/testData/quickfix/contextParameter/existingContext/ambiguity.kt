// "Add argument to existing 'context'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// IGNORE_K1
// DISABLE_K2_ERRORS
interface Logger { fun log(msg: String) }
class Other

context(l: Logger) fun emit() { l.log("x") }

fun caller(logger: Logger) {
    with(logger) {
        with(Other()) {
            context("hello") {
                <caret>emit()
            }
        }
    }
}