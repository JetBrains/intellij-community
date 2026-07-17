// "Create annotation 'bar'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Change visibility…
// K2_ACTIONS_LIST: Create parameter 'bar'
// K2_ACTIONS_LIST: Create test
// K2_ACTIONS_LIST: Put arguments on separate lines
// K2_ACTIONS_LIST: Rename reference
// K2_ACTIONS_LIST: Create annotation 'bar'
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: ANNOTATION_ARGUMENT_MUST_BE_CONST
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

@[foo(1, "2", <caret>bar("3", 4))] fun test() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix$LowPriorityCreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction