// DISABLE_ERRORS
annotation class Ann

expect class Foo(p: Any?) {
  @Ann
  constructor()
}
