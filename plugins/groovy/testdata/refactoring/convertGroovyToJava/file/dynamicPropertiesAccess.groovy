class A {
  def foo() {
    bar = 2
    print(bar = 3)
    def s = "a"
    s.bar = 4
    print(s.bar =5)

    print bar
    print s.bar
  }
}