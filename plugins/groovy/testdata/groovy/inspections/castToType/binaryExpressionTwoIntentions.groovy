import groovy.transform.CompileStatic

class C {
  def plus(Integer i) {

  }
  def plus(Double d) {

  }
}
@CompileStatic
def foo() {
  new C() +<caret> ""
}
