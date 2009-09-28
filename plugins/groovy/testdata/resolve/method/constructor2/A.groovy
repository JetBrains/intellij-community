class Clazz {
  def foo

  Clazz(Map m) {
    println "in parameterless constructor"
  }

  Clazz(String s) {
    println "in one parameter constructor"
  }
}

def z = new <ref>Clazz()

println z.foo
