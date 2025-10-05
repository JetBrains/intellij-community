class X {
  static void f() {
    def x = false

    def z = !(true ? true : false) ==><caret> x
    println(z)
  }
}