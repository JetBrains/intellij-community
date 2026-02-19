// "Create annotation 'bar'" "true"
// ERROR: Unresolved reference: foo
// IGNORE_K2
@[foo(1, "2", <caret>bar<String, Int>("3", 4))] fun test() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix$LowPriorityCreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction