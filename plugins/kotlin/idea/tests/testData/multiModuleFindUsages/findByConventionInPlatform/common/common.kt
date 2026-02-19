class DummyClass002
expect operator fun DummyClass002.invoke()
fun testInvokeCommon(d: DummyClass002) {
    d()
    d.invoke()
}