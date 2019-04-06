import groovy.transform.CompileStatic

@CompileStatic
def test() {
  def cl = () -> {
    def var
    var = new Date()
  }
  def var = "abc"

  cl()
  var.toUpperCase()  //no errors
}
