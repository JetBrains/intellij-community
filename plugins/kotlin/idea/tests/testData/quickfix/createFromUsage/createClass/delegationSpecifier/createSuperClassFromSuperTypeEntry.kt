// "Create class 'Unknown'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
class A : Unknown<caret> {
    constructor()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction