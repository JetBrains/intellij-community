// "Use 'this@with' as receiver" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_AFTER_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Foo {
    fun bar() {
    }
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun test(foo: Foo) {
    with(foo) {
        with("inner receiver") {
            withFoo {
                ba<caret>r()
            }
        }
    }
}
