import groovy.transform.CompileStatic

class C {
  def plus(String s) {

  }
}
@CompileStatic
def foo() {
  new C() +<caret> 1
}