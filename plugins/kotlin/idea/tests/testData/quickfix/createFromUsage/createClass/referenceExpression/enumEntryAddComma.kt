// "Create enum constant 'PUBLIC'" "true"
// K2_ERROR: Unresolved reference 'PUBLIC'.
enum class TopicState {
    PRIVATE
}

fun foo() {
    TopicState.<caret>PUBLIC
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction