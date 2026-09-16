// "Create annotation 'foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

@[<caret>foo()] fun test() {

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction