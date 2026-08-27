// "Surround call with 'context(TODO())'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// K2_ERROR: NONE_APPLICABLE
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int)
fun String.foo(): String {
    return this + i.toString()
}

fun test() {
    val v: String = "abc"
    println(v::foo<caret>)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix