// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages, expected
class DummyClass001
expect operator fun DummyClass001.comp<caret>onent1()
expect operator fun DummyClass001.component2()
fun testDummyClass001() {
    val (a, b) = DummyClass001()
    DummyClass001().component1()
}