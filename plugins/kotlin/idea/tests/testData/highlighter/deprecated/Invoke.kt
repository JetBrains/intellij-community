class MyRunnable() {}

@Deprecated("Use A instead") operator fun MyRunnable.invoke() {
}

fun test() {
  val m = MyRunnable()
  <warning descr="[DEPRECATION]">m</warning>()
}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
