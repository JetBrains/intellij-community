// "Create secondary constructor" "true"
// K2_ACTION: "Add secondary constructor to 'Creation'" "true"
// ERROR: There's a cycle in the delegation calls chain
// K2_AFTER_ERROR: CYCLIC_CONSTRUCTOR_DELEGATION_CALL
// K2_ERROR: NO_VALUE_FOR_PARAMETER
class Creation(val f: Int)
val v = Creation(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.AddConstructorFix