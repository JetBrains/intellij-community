interface I {
  fun m(): String
}

class C : I {
  override fun m() = "something"
}

fun use() {
  val c = C()
  val i: I = c
  c.m()
  i.m()
}
