class KtNullability {
  private fun <T> T?.f(): T? = this
  private fun <T> T?.g(): T? = null

  fun testNullCheckOperator1(a: Int?) = a ?: 42
  fun testNullCheckOperator2(a: Int?) = a ?: 42

  fun testNullCheckSequence1(a: Int?, b: Int?) = a ?: b ?: 42
  fun testNullCheckSequence2(a: Int?, b: Int?) = a ?: b ?: 42

  fun testSafeCall1(a: Int?) = a?.toString()
  fun testSafeCall2(a: Int?) = a?.toString()

  fun testSafeCallSequence1(a: Int?) = a?.f()?.f()
  fun testSafeCallSequence2(a: Int?) = a?.g()?.f()
}
