// "Create annotation 'bar'" "true"
// ERROR: Unresolved reference: foo

@[foo(1, "2", <caret>bar(fooBar = "3"))] fun test() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix$LowPriorityCreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction