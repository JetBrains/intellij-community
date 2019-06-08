import groovy.transform.CompileStatic

class C {
  def plus(String s) {

  }
}
@CompileStatic
def foo() {
  new C() + (1 as String)
}