// "Use 'this' as receiver" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER
// K2_AFTER_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

open class Foo {
    fun bar() {
    }
}

fun withFoo(f: context(Foo) () -> Unit) {}

fun test() {
    val obj = object : Foo() {
        fun go() {
            with("inner receiver") {
                withFoo {
                    ba<caret>r()
                }
            }
        }
    }
}
