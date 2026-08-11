// "Create class 'A!u00A0'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
fun test() {
    val t = <caret>`A!u00A0`(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction