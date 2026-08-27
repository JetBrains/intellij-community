// "Surround call with 'context(TODO())'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(x: String)
fun makeError(): RuntimeException {
    return RuntimeException(x)
}

fun <T> compute(f: () -> T): T = f()

fun main() {
    throw compute(<caret>::makeError)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix