// "Create secondary constructor" "true"
// ERROR: There's a cycle in the delegation calls chain
class Creation(val f: Int)
val v = Creation(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix