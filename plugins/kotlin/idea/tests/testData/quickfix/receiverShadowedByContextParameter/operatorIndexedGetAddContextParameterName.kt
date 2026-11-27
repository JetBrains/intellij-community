// "Surround with 'with(f) { ... }'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Bag

class Foo {
    operator fun Bag.get(i: Int): String = ""
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    context(f: Foo)
    fun local() {
        val g = Bag()<caret>[0]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
