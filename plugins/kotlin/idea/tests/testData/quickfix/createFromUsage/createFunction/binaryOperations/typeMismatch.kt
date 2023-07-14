// "Create extension function 'A.times'" "true"
class A

operator fun A.times(i: Int) = this

fun test() {
    A() * <caret>"1"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix