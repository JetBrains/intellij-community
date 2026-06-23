// "Add explicit context 's: String'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: Overload resolution ambiguity between candidates:<br>fun foo(p: Int): Unit<br>context(s: String) fun foo(p: Int): Unit
fun foo(p: Int) {}
context(s: String) fun foo(p: Int) {}

context(p: String)
fun test() {
    f<caret>oo(p = 5)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFixFactory$AddExplicitContextArgumentFix
