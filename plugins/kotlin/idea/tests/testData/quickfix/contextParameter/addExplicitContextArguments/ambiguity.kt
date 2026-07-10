// "Add explicit context argument" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
class Logger(val tag: String)

context(log: Logger)
fun process(data: String) {}

fun process(data: String, log: Logger) {}

fun test() {
    <caret>process(Logger("t"))
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix