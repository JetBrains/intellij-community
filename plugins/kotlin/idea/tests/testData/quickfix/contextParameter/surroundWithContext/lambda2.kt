// "Surround call with 'context(TODO())'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(x: String)
fun shout(): String {
    return x.uppercase() + "!"
}

fun compute(f: () -> String): String = f()

fun main() {
    compute { <caret>shout() }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix