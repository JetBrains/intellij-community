// "Add explicit context 's: String'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.3
// K2_ERROR: Overload resolution ambiguity between candidates:<br>fun foo(i: Int): Unit<br>context(s: String) fun foo(i: Int): Unit
fun foo(i: Int) {}
context(s: String) fun foo(i: Int) {}

context(s: String)
val test: Int
    get() {
        f<caret>oo(1)
        return 0
    }

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFixFactory$AddExplicitContextArgumentFix
