class A {
  def <error descr="Type parameters are unexpected"><T, J></error> t = 5
  def foo = 42
}

def <error descr="Type parameters are unexpected"><T></error> hello = 42
