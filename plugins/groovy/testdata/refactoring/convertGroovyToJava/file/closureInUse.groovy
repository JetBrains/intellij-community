class IntCat {
  static def call(Integer i) {print i}

  static def call(Integer i, String s) {print s}
}

use(IntCat) {
  2.call()
  2.call("a")
  2()
  2("2")
}