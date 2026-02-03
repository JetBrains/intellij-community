import groovy.transform.CompileStatic

@CompileStatic
def m() {
  List<String> l = []
  l.findAll((Object o) -> false)
}
