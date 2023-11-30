// "Implement members" "false"
// ACTION: Extract 'A' from current file
// ACTION: Make internal

interface I {
    fun foo()
}

@Suppress("NOT_A_MULTIPLATFORM_COMPILATION")
expect <caret>class A : I

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.IntentionActionAsQuickFixWrapper

// Because KT-59739 is implemented only for K2
/* IGNORE_K2 */
