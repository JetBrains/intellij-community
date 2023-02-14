// WITH_STDLIB
enum class Test(private vararg val names: String) : Iterable<String> {
  A("a", "hello", "by");

  override fun iterator(): Iterator<String> {
    return names.iterator()
  }
}

fun test() {
  for(name in Test.A) { // 'for' range is always empty
    println(name)
  }
}