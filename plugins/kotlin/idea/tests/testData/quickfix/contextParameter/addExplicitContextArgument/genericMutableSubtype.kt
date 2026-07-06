// "Add explicit context 'l: List<String>'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: OVERLOAD_RESOLUTION_AMBIGUITY
fun foo(i: Int) {}
context(l: List<String>) fun foo(i: Int) {}

context(l: MutableList<String>)
fun test() {
    f<caret>oo(1)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFixFactory$AddExplicitContextArgumentFix
