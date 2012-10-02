class <warning descr="Class Test is unused">Test</warning> {
  int <warning descr="Property x is unused">x</warning>
def <warning descr="Method foo is unused">foo</warning>(String <warning descr="Parameter s is unused">s</warning>) {}


def methodMissing(String name, def args) {
  return null  //To change body of created methods use File | Settings | File Templates.
}

def propertyMissing(String name) {
  return null  //To change body of created methods use File | Settings | File Templates.
}

def propertyMissing(String name, def arg) {
  //To change body of created methods use File | Settings | File Templates.
}
}