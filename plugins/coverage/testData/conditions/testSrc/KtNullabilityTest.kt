import junit.framework.TestCase

class KtNullabilityTest : TestCase() {
  fun testKotlinNullability(): Unit = KtNullability().run {
    testNullCheckOperator1(1)
    testNullCheckOperator2(null)
    testNullCheckSequence1(null, 1)
    testNullCheckSequence2(null, null)

    testSafeCall1(1)
    testSafeCall2(null)
    testSafeCallSequence1(1)
    testSafeCallSequence2(1)
  }
}