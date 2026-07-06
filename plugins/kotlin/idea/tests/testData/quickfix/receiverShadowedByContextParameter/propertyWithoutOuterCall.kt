// "Use 'this' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

class Foo {
    val bar get() = ""

    context(_: Foo)
    fun baz() {
        ba<caret>r
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitThisFix
