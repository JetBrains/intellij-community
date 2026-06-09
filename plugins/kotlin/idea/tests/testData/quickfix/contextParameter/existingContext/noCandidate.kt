// "Add argument to existing 'context'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// IGNORE_K1
// DISABLE_K2_ERRORS
interface MyLogger { fun log(msg: String) }

context(l: MyLogger) fun emit() { l.log("x") }

fun repro() {
    context("hello") {
        <caret>emit()
    }
}