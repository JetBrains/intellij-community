var x = 5

val Int.sq : Int
get() {
  return this * this
}

val y : Int = 1
get() {
  return 5.sq + field + x
}

class Foo(
    val a : Int,
    b : String,
    var c : String
) {
  init {
    b
  }

  fun f(p : Int = a) {}

  var v : Int
  get() {
    return 1;
  }
  set(value) {
    value
  }
}
