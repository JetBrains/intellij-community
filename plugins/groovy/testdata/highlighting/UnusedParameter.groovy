def <warning descr="Method foo is unused">foo</warning>(int <warning descr="Parameter x is unused">x</warning>) {
  for (String d : args) {

  }
}

def <warning descr="Method bar is unused">bar</warning>(int x) {
  print x
}

class A {
  def <warning descr="Method foo is unused">foo</warning>(int x) {}
  def <warning descr="Method bar is unused">bar</warning>(int <warning descr="Parameter y is unused">y</warning>) {}
}

class <warning descr="Class B is unused">B</warning> extends A {
  def foo(int x) {}
}
