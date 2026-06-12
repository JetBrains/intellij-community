// "Add explicit context argument" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
class Logger(val tag: String)

context(log: Logger)
fun process(data: String) {}

context(env: String)
fun process(data: String, log: Logger) {}

context(env: String)
fun test() {
    <caret>process(data = "hello")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix