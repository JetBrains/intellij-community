actual operator fun DummyClass001.component1() {}
actual operator fun DummyClass001.component2() {}
fun testDummyClass001Jvm() {
    val (a, b) = DummyClass001()
    DummyClass001().component1()
}
