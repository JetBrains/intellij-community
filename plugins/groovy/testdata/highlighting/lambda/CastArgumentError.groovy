import groovy.transform.CompileStatic

@CompileStatic
def m() {
  List<Object> l = []
  l.findAll (<error>Integer</error> o) -> { false }
}
