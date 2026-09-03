// "Use 'contextOf<List<String>>()' as receiver" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: RECEIVER_SHADOWED_BY_CONTEXT_PARAMETER

fun List<String>.bar() {
}

fun withList(f: context(List<String>) () -> Unit) {}

fun List<String>.test() {
    withList {
        val ref = ::ba<caret>r
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitReceiverFix
