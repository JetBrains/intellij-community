// "Use 'this@run' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Foo {
    fun bar() {
    }
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun test(foo: Foo) {
    foo.run {
        with("inner receiver") {
            withFoo {
                ba<caret>r()
            }
        }
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
