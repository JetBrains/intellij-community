// "Create extension function 'A.set'" "true"
class A

fun A.set(i: Int, j: Int) {

}

fun test() {
    A()[<caret>"1"] = 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix