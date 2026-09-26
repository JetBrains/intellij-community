class X {
  static void f() {
    def x = false

    def z = !x ==> (true ? true : false)
    println(z)
  }
}