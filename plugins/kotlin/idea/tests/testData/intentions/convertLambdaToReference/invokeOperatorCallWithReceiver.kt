fun myInvoke(f: () -> Unit) = f()

class InvokeContainer {
    operator fun invoke() {}
}

class C(val k: InvokeContainer)

fun test(c: C) {
    myInvoke { <caret>c.k() }
}
