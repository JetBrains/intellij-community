// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters

object Context

fun foo() {
    wi<caret>th(Context) {
        bar()
    }
}

context(s: Context) fun bar() {}

// IGNORE_K1