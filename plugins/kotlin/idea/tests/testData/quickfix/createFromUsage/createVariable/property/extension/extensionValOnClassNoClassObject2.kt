// "Create extension property 'A.Companion.foo'" "true"
class A

fun test() {
    val a: Int = A.<caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix