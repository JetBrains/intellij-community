import foo.A

class X {
  void abc(A a) {}
  void cde() {
    abc(A.Const)
  }
}