class Base {
  def f<caret>oo(int newName) {
    print newName
  }
}

class Inh extends Base {
  def foo(int otherParam) {
    print otherParam
  }
}