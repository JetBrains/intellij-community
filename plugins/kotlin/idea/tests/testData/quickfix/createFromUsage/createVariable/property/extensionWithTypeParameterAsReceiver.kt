// "Create extension property 'T.bar'" "true"
fun consume(n: Int) {}

fun <T> foo(t: T) {
    consume(t.<caret>bar)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix