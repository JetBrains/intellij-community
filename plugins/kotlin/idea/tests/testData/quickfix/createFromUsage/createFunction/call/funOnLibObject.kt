// "Create extension function 'Unit.foo'" "true"
// WITH_STDLIB

fun test() {
    val a: Int = Unit.<caret>foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix