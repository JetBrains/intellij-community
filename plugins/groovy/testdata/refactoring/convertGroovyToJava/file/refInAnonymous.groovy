class A {
  def foo () {
    def x = 2

    new Runnable() {
      void run() {
        x = 4
        print x
      }
    }.run()
  }
}