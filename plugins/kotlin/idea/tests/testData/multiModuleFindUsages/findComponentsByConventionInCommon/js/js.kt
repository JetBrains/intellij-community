actual operator fun DummyClass001.component1() {}
actual operator fun DummyClass001.component2() {}
fun testDummyClass001Js() {
    val (a, b) = DummyClass001()
    DummyClass001().component1()
}
