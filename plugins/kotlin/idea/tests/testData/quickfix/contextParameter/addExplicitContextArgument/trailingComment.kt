// "Add explicit context 's: String'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: OVERLOAD_RESOLUTION_AMBIGUITY
fun foo(i: Int) {}
context(s: String) fun foo(i: Int) {}

context(s: String)
fun test() {
    f<caret>oo(1 /* keep me after 1 */,
    /* and me too */)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFixFactory$AddExplicitContextArgumentFix
