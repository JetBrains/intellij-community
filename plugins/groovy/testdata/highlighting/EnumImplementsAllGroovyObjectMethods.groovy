enum E {
  a, b, c
}

interface I {
  def foo()
}

<error descr="Method 'foo' is not implemented">enum EI implements I</error> {

}