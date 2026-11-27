// "Use 'this@`my test`' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Foo {
    fun bar() {
    }
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.`my test`() {
    with("inner receiver") {
        withFoo {
            bar<caret>()
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
