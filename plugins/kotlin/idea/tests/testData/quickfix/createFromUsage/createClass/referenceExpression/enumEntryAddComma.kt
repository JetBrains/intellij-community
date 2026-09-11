// "Create enum constant 'PUBLIC'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
enum class TopicState {
    PRIVATE
}

fun foo() {
    TopicState.<caret>PUBLIC
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction