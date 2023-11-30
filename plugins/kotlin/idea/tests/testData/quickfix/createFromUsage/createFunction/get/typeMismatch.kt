// "Create extension function 'A.get'" "true"
class A

fun A.get(i: Int) = this

fun test() {
    A()[<caret>"1"]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix