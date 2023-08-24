// "Create extension function 'A.foo'" "true"
fun bar(b: Boolean) {

}

class A(val n: Int)

fun A.test() {
    bar(<caret>foo(n))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix