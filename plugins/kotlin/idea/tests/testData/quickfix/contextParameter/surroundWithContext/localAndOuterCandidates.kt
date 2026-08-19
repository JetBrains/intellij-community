// "Surround call with 'context(innerLogger)'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
interface MyLogger { fun log(msg: String) }
class ConsoleLogger : MyLogger { override fun log(msg: String) {} }

context(l: MyLogger) fun emit() { l.log("x") }

fun repro() {
    val outerLogger: MyLogger = ConsoleLogger()
    context("hello") {
        val innerLogger: MyLogger = ConsoleLogger()
        <caret>emit()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix