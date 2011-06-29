enum E {
  a, b, c
}

interface I {
  def foo()
}

enum <error descr="Method 'foo' is not implemented">EI</error> implements I {

}