// "Create extension property 'String?.notExistingVal'" "true"
fun foo(n: Int) {}

fun context(p: String?) {
    foo(p.<caret>notExistingVal)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix