import groovy.transform.CompileStatic

@CompileStatic
def test() {
  def var = "abc"
  def cl = () -> {
    var = new Date()
  }
  cl()
  var.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>()
}
