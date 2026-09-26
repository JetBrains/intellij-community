actual operator fun DummyClass002.invoke() {}
fun testInvokeJvm(d: DummyClass002) {
    d()
    d.invoke()
}