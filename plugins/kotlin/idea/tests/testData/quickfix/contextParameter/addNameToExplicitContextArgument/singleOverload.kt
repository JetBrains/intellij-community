// "Add name to argument: 'log = Logger("t")'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
class Logger(val tag: String)

context(log: Logger)
fun process(data: String) {}

fun test() {
    <caret>process(Logger("t"))
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix