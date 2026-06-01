// "Add argument to existing 'context'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// IGNORE_K1
// DISABLE_K2_ERRORS
interface Logger { fun log(msg: String) }
class Service : Logger { override fun log(msg: String) {} }

context(l: Logger) fun emit() { l.log("x") }

class App {
    val s = Service()
    fun caller() {
        with(s) {
            context("hello") {
                <caret>emit()
            }
        }
    }
}