fun myInvoke(f: () -> Unit) = f()

class InvokeContainer {
    operator fun invoke() {}
}

fun test(k: InvokeContainer) {
    myInvoke { <caret>k() }
}