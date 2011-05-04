def foo() {
  def list =[1, 2, 3]
  list.each {
    foo()
  }
}