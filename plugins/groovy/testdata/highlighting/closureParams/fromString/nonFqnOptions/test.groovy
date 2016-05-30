import groovy.transform.CompileStatic

@CompileStatic
class B {
  def foo(BufferedReader r) {
    r.eachLine { String a -> }
  }

  def bar() {
    A.foo { BigInteger b -> }
  }
}
