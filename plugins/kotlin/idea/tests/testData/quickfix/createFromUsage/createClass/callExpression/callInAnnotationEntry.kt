// "Create annotation 'bar'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Change visibility…
// K2_ACTIONS_LIST: Put arguments on separate lines
// K2_ACTIONS_LIST: Create annotation 'bar'
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

@[foo(1, "2", <caret>bar("3", 4))] fun test() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix$LowPriorityCreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction