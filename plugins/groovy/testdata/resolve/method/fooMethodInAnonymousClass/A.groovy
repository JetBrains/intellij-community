class A{
  def foo(){}
}

def foo() {}

new A() {
  def a(){
    fo<caret>o()
  }
}
