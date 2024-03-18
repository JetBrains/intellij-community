class KtNullability {
  private fun <T> T?.f(): T? = this
  private fun <T> T?.g(): T? = null

  fun testNullCheckOperator1(a: String?): String = a ?: "42"
  fun testNullCheckOperator2(a: String?): String = a ?: "42"
  fun testPrimitiveNullCheckOperator(a: Int?) = a ?: 42

  fun testNullCheckSequence1(a: String?, b: String?) = a ?: b ?: "42"
  fun testNullCheckSequence2(a: String?, b: String?) = a ?: b ?: "42"

  fun testSafeCall1(a: String?) = a?.f()
  fun testSafeCall2(a: String?) = a?.f()
  fun testPrimitiveSafeCall1(a: Int?) = a?.f()

  fun testSafeCallSequence1(a: String?) = a?.f()?.f()
  fun testSafeCallSequence2(a: String?) = a?.g()?.f()
}
