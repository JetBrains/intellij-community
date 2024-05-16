// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages, expected
actual  operator fun DummyClass002.inv<caret>oke() {}
fun testInvokeJs(d: DummyClass002) {
    d()
    d.invoke()
}

// IGNORE_K1