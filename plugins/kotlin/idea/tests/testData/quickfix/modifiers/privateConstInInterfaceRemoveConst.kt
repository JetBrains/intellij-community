// "Remove 'const' modifier" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks
// K2_ERROR: PRIVATE_CONST_IN_INTERFACE

interface I {
    companion {
        pri<caret>vate const val X_1 = 1
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
