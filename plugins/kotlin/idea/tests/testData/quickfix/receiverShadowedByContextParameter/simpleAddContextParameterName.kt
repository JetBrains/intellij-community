// "Use 'f' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters

class Foo {
    fun bar() {
    }
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    context(f: Foo)
    fun local() {
        bar<caret>()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterReceiverFix
