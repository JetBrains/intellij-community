// DISABLE_ERRORS
annotation class Ann

interface I {
  fun foo() {}
}

expect class Foo : I {
  @Ann
  override fun foo()
}
