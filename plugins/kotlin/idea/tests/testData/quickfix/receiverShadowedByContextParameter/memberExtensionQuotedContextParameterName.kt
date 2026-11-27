// "Surround with 'with(`my foo`) { ... }'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Foo {
    fun String.bar() {
    }
}

fun Foo.test() {
    context(`my foo`: Foo)
    fun local() {
        "".bar<caret>()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
