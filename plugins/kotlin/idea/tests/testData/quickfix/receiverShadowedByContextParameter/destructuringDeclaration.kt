// "Surround with 'with(this) { ... }'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_AFTER_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_AFTER_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Bag

class Foo {
    operator fun Bag.component1(): Int = 0
    operator fun Bag.component2(): Int = 0
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    withFoo {
        val (<caret>a, b) = Bag()
    }
}

