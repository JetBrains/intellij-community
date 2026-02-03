class DummyClass001
expect operator fun DummyClass001.component1()
expect operator fun DummyClass001.component2()
fun testDummyClass001() {
    val (a, b) = DummyClass001()
    DummyClass001().component1()
}