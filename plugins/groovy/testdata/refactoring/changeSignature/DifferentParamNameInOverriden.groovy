class Base {
  def f<caret>oo(int param) {
    print param
  }
}

class Inh extends Base {
  def foo(int otherParam) {
    print otherParam
  }
}