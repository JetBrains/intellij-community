// "Use 'f' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Call to 'fun bar(): Unit' defined in 'Foo' uses an implicit receiver shadowed by a context parameter. Make the receiver explicit using 'this' or 'f'.

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
