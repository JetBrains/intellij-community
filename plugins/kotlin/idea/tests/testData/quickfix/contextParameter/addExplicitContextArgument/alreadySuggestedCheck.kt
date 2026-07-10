// "Add explicit context 's: String, a: String'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: OVERLOAD_RESOLUTION_AMBIGUITY
fun foo(i: Int) {}
context(s: String, a: String) fun foo(i: Int) {}
context(j: Int) fun foo(i: Int) {}

context(s: String)
fun test() {
    <caret>foo(1)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFixFactory$AddExplicitContextArgumentFix
