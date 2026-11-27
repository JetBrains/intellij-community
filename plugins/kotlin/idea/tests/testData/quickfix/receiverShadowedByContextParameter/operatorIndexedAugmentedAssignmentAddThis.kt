// "Surround with 'with(this) { ... }'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_ERROR: UNRESOLVED_REFERENCE

class Bag

class Foo {
    operator fun Bag.get(i: Int): Int = 0
    operator fun Bag.set(i: Int, v: Int) {}
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    val b = Bag()
    withFoo {
        b<caret>[0] += 1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
