// "Create extension function 'Boolean.unaryMinus'" "true"
// WITH_STDLIB

fun test() {
    val a = <caret>-false
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix