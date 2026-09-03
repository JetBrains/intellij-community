// "Use 'this@Foo' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

fun withFoo(f: context(Foo) () -> Unit) {}

class Foo {
    fun bar() {
    }

    fun test() {
        with("inner receiver") {
            withFoo {
                ba<caret>r()
            }
        }
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
