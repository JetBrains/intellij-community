interface I {
  /**
   * KDoc for iface method
   */
  fun m()
}

class C : I {
  /**
   * KDoc for impl
   */
  override fun m() {}
}

/**
 * KDoc for use
 */
fun use(i: I) {
  i.m()
}
