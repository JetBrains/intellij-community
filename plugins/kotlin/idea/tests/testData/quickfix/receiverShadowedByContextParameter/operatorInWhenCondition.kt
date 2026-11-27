// "Surround with 'with(this) { ... }'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_AFTER_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Bag

class Foo {
    operator fun Bag.contains(o: Int): Boolean = true
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun Foo.test() {
    withFoo {
        when (1) {
            <caret>in Bag() -> {}
            else -> {}
        }
    }
}

