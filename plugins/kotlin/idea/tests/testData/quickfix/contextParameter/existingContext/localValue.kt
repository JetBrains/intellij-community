// "Add argument to existing 'context'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// IGNORE_K1
// DISABLE_K2_ERRORS
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
interface Logger { fun log(msg: String) }
class ConsoleLogger : Logger { override fun log(msg: String) {} }

context(l: Logger) fun emit() { l.log("x") }

fun repro() {
    val l: Logger = ConsoleLogger()
    context("hello") {
        <caret>emit()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterToExistingContextFix