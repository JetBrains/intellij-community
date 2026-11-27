// "Surround with 'with(this) { ... }'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Bag

class Foo {
    operator fun Bag.plus(o: Int): Bag = this
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    withFoo {
        val b = Bag() <caret>+ 1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
