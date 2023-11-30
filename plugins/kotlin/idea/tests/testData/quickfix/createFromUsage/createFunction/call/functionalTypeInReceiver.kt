// "Create extension function '((Int) -> String).bar'" "true"

fun foo(block: (Int) -> String) {
    block.b<caret>ar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix