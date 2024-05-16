// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages

class DummyClass002
expect operator fun DummyClass002.invoke<caret>()
fun testInvokeCommon(d: DummyClass002) {
    d()
    d.invoke()
}