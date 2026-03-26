interface I {
  /**
   * KDoc for default
   */
  fun d() {}
}

fun call() {
  val i: I = object : I {}
  i.d()
}
