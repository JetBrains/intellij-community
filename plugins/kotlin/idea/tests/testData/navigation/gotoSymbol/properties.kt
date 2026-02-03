val testGlobal = 12

fun some() {
  val testInFun = 12
}

interface SomeInterface {
  val testInInterface
}

class Some() {
  val testInClass = 12

  companion object {
    val testInClassObject = 12
  }
}

class SomePrimary(val testInPrimary: Int)

// SEARCH_TEXT: test
